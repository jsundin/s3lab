package ng3.agent;

import ng3.BackupDirectory;
import ng3.BackupPlan;
import ng3.common.ShutdownSynchronizer;
import ng3.common.SimpleThreadFactory;
import ng3.conf.Configuration;
import ng3.conf.DirectoryConfiguration;
import ng3.db.DbClient;
import ng3.drivers.BackupDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class BackupAgent {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ShutdownSynchronizer shutdownSynchronizer;
  private final UUID planId;
  private final DbClient dbClient;
  private final Configuration configuration;
  private CountDownLatch countDownLatch;

  public BackupAgent(ShutdownSynchronizer shutdownSynchronizer, UUID planId, DbClient dbClient, Configuration configuration) {
    this.shutdownSynchronizer = shutdownSynchronizer;
    this.planId = planId;
    this.dbClient = dbClient;
    this.configuration = configuration;
  }

  public boolean run(boolean failOnBadDirectories, boolean forceBackupNow, boolean forceVersioningNow, boolean runOnce) {
    final List<BackupDirectory> backupDirectories = Collections.unmodifiableList(findBackupDirectories());
    if (failOnBadDirectories) {
      if (!checkDirectories(backupDirectories)) {
        return false;
      }
      logger.debug("All configured directories seem to be accessible");
    }

    long t0 = System.currentTimeMillis();
    countDownLatch = new CountDownLatch(1 + (configuration.getVersioning() == null ? 0 : 1));
    shutdownSynchronizer.addListener(shutdownListener);

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new SimpleThreadFactory("BackupTask"));
    List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    BackupPlan plan = dbClient.getBackupPlan(planId);
    scheduledTasks.add(scheduleTask(plan.getLastStarted(), configuration.getIntervalInMinutes(), forceBackupNow, runOnce, scheduler, countDownLatch, () -> runBackup(backupDirectories)));
    scheduledTasks.add(scheduleTask(plan.getLastVersioned(), configuration.getVersioning().getIntervalInMinutes(), forceVersioningNow, runOnce, scheduler, countDownLatch, () -> runVersioning()));

    while (true) {
      try {
        countDownLatch.await();
        break;
      } catch (InterruptedException ignored) {
        Thread.interrupted();
      }
    }
    shutdownSynchronizer.removeListener(shutdownListener);
    for (ScheduledFuture<?> scheduledTask : scheduledTasks) {
      scheduledTask.cancel(false);
    }
    scheduler.shutdown();
    while (true) {
      try {
        scheduler.awaitTermination(999, TimeUnit.DAYS); // TODO: -> Settings
        break;
      } catch (InterruptedException ignored) {
        Thread.interrupted();
      }
    }
    logger.info("BackupAgent shut down after {}", TimeUtils.formatMillis(System.currentTimeMillis() - t0));
    return false;
  }

  private ScheduledFuture<?> scheduleTask(ZonedDateTime lastExecution, int intervalInMinutes, boolean forceNow, boolean runOnce, ScheduledExecutorService scheduler, CountDownLatch countDownLatch, Runnable task) {
    long initialDelay = computeInitialDelay(lastExecution, intervalInMinutes, forceNow);
    ScheduledFuture<?> taskFuture[] = new ScheduledFuture[1];
    taskFuture[0] = scheduler.scheduleAtFixedRate(() -> {
      boolean failed = false;

      try {
        task.run();
      } catch (Throwable error) {
        logger.error("Unhandled internal error", error);
        failed = true;
      }

      if (runOnce || failed) {
        taskFuture[0].cancel(false);
        countDownLatch.countDown();
      }
    }, initialDelay, intervalInMinutes, TimeUnit.MINUTES);
    return taskFuture[0];
  }

  private void runBackup(List<BackupDirectory> backupDirectories) {
    BackupPlan plan = dbClient.getBackupPlan(planId);
    logger.debug("runBackup(), time since last started: {}", debugTimeSinceLast(plan.getLastStarted()));

    plan.setLastStarted(ZonedDateTime.now());
    dbClient.saveBackupPlan(plan);

    BackupReportWriter report = new BackupReportWriter();
    report.setStartedAt(ZonedDateTime.now());
    BackupDriver.BackupSessionNG session = configuration.getBackupDriver().startSession(dbClient, configuration, report, backupDirectories);
    new FileScanner(dbClient, report, backupDirectories).scan();
    session.endSession();

    report.setFinishedAt(ZonedDateTime.now());
  }

  private void runVersioning() {
    BackupPlan plan = dbClient.getBackupPlan(planId);
    logger.debug("runVersioning(), time since last started: {}", debugTimeSinceLast(plan.getLastVersioned()));

    plan.setLastVersioned(ZonedDateTime.now());
    dbClient.saveBackupPlan(plan);
  }

  private Runnable shutdownListener = () -> {
    logger.debug("External shutdown");
    while (countDownLatch.getCount() > 0) {
      countDownLatch.countDown();
    }
  };

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

  private long computeInitialDelay(ZonedDateTime lastExecution, int intervalInMinutes, boolean now) {
    if (now || lastExecution == null) {
      return 0;
    }
    long minutesSinceLast = ChronoUnit.MINUTES.between(lastExecution, ZonedDateTime.now());
    return intervalInMinutes - minutesSinceLast;
  }

  private class ConfiguredDirectory {
    private final UUID id;
    private final File directory;

    private ConfiguredDirectory(UUID id, File directory) {
      this.id = id;
      this.directory = directory;
    }
  }

  private String debugTimeSinceLast(ZonedDateTime lastExecution) {
    return lastExecution == null ? "(never started)" : TimeUtils.formatMillis(ChronoUnit.MILLIS.between(lastExecution, ZonedDateTime.now()));
  }
}
