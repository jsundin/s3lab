package ng3.agent;

import ng3.BackupDirectory;
import ng3.BackupPlan;
import ng3.common.LatchSynchronizer;
import ng3.conf.Configuration;
import ng3.conf.DirectoryConfiguration;
import ng3.db.DbClient;
import ng3.drivers.BackupDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class BackupAgent {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final LatchSynchronizer shutdownSynchronizer;
  private final UUID planId;
  private final DbClient dbClient;
  private final Configuration configuration;
  private final Semaphore agentLock = new Semaphore(0);

  public BackupAgent(LatchSynchronizer shutdownSynchronizer, UUID planId, DbClient dbClient, Configuration configuration) {
    this.shutdownSynchronizer = shutdownSynchronizer;
    this.planId = planId;
    this.dbClient = dbClient;
    this.configuration = configuration;
  }

  public boolean run(boolean failOnBadDirectories, boolean forceBackupNow, boolean runOnce) throws Exception {
    final List<BackupDirectory> backupDirectories = Collections.unmodifiableList(findBackupDirectories());
    if (failOnBadDirectories) {
      if (!checkDirectories(backupDirectories)) {
        return false;
      }
      logger.debug("All configured directories seem to be accessible");
    }

    shutdownSynchronizer.addSemaphore(agentLock);

    long t0 = System.currentTimeMillis();
    BackupTaskController backupTaskController = new BackupTaskController(backupDirectories);
    ScheduledBackupTask backupTask = new ScheduledBackupTask(backupTaskController, runOnce);
    backupTask.scheduleTask(forceBackupNow);

    agentLock.acquire(); // wait for jobs to complete
    shutdownSynchronizer.removeSemaphore(agentLock);
    backupTask.shutdown();

    logger.info("Executed {} backups in {}, finishing {}", backupTask.getExecutionCount(), TimeUtils.formatMillis(System.currentTimeMillis() - t0), backupTaskController.hasErrors() ? "with error(s)" : "successfully");
    return !backupTaskController.hasErrors();
  }

  private boolean checkDirectories(List<BackupDirectory> backupDirectories) {
    boolean hasErrors = false;
    for (BackupDirectory directory : backupDirectories) {
      File path = directory.getConfiguration().getDirectory();
      if (!path.exists()) {
        hasErrors = true;
        logger.error("Directory '{}' does not exist", path);
      } else if (!path.isDirectory()) {
        hasErrors = true;
        logger.error("Directory '{}' is not a directory", path);
      } else if (!path.canRead() || !path.canExecute() || path.listFiles() == null) {
        hasErrors = true;
        logger.error("Cannot access directory '{}' properly", path);
      }
    }
    return !hasErrors;
  }

  private List<BackupDirectory> findBackupDirectories() {
    Map<File, ConfiguredDirectory> savedDirectories = dbClient.buildQuery("select directory_id, directory from directory where plan_id=?")
            .withParam().uuidValue(1, planId)
            .executeQuery(rs -> new ConfiguredDirectory(
                    rs.getUuid(1),
                    rs.getFile(2)
            )).stream()
            .collect(Collectors.toMap(k -> k.directory, v -> v));

    List<ConfiguredDirectory> removedDirectories = savedDirectories.values().stream()
            .filter(v -> {
              for (DirectoryConfiguration dir : configuration.getDirectories()) {
                if (dir.getDirectory().equals(v.directory)) {
                  return false;
                }
              }
              return true;
            })
            .collect(Collectors.toList());

    List<BackupDirectory> backupDirectories = new ArrayList<>();
    List<BackupDirectory> newDirectories = new ArrayList<>();
    for (DirectoryConfiguration directory : configuration.getDirectories()) {
      if (savedDirectories.containsKey(directory.getDirectory())) {
        BackupDirectory backupDirectory = new BackupDirectory(savedDirectories.get(directory.getDirectory()).id, directory);
        backupDirectories.add(backupDirectory);
      } else {
        BackupDirectory backupDirectory = new BackupDirectory(UUID.randomUUID(), directory);
        newDirectories.add(backupDirectory);
      }
    }

    dbClient.buildQuery("insert into directory (directory_id, plan_id, directory) values (?, ?, ?)")
            .executeUpdate(newDirectories, (s, v) -> {
              v.uuidValue(1, s.getId());
              v.uuidValue(2, planId);
              v.fileValue(3, s.getConfiguration().getDirectory());
            });
    backupDirectories.addAll(newDirectories);

    logger.info("Found {} active directories ({} new and {} was ignored)", backupDirectories.size(), newDirectories.size(), removedDirectories.size());
    return backupDirectories;
  }

  private class ConfiguredDirectory {
    private final UUID id;
    private final File directory;

    private ConfiguredDirectory(UUID id, File directory) {
      this.id = id;
      this.directory = directory;
    }
  }

  private class BackupTaskController implements ScheduledBackupTask.Controller {
    private Throwable error;
    private final List<BackupDirectory> backupDirectories;

    private BackupTaskController(List<BackupDirectory> backupDirectories) {
      this.backupDirectories = backupDirectories;
    }

    @Override
    public void backupTaskStarted() {
      BackupPlan backupPlan = dbClient.getBackupPlan(planId);
      backupPlan.setLastStarted(ZonedDateTime.now());
      dbClient.saveBackupPlan(backupPlan);
    }

    @Override
    public ZonedDateTime getNextExecutionTime() {
      ZonedDateTime next = null;
      BackupPlan backupPlan = dbClient.getBackupPlan(planId);
      if (backupPlan.getLastStarted() != null) {
        next = backupPlan.getLastStarted().plusMinutes(configuration.getIntervalInMinutes());
        if (next.isBefore(ZonedDateTime.now())) {
          next = null;
        }
      }
      return next;
    }

    @Override
    public void executeJob() {
      BackupReportWriter report = new BackupReportWriter();
      report.setStartedAt(ZonedDateTime.now());

      BackupDriver.BackupSession backupSession = configuration.getBackupDriver().startSession(dbClient, report, planId);

      new FileScanner(dbClient, report, backupDirectories).scan();

      backupSession.finish();

      report.setFinishedAt(ZonedDateTime.now());
      System.out.println("----");
      System.out.println(report);
      System.out.println("----");
    }

    @Override
    public void schedulingFinished() {
      agentLock.release();
    }

    @Override
    public void onError(Throwable t) {
      logger.error("Unhandled error", t);
      error = t;
      agentLock.release();
    }

    boolean hasErrors() {
      return error != null;
    }
  }
}
