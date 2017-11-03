package ng3.agent;

import ng3.BackupDirectory;
import ng3.common.BlockingLatch;
import ng3.common.PidFileWriter;
import ng3.conf.Configuration;
import ng3.conf.DirectoryConfiguration;
import ng3.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BackupAgent {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BackupAgentParams params;
  private final DbClient dbClient;
  private final Configuration configuration;
  private final BlockingLatch executionLatch = new BlockingLatch("ExecutionLatch");

  public BackupAgent(BackupAgentParams params, DbClient dbClient, Configuration configuration) {
    this.params = params;
    this.dbClient = dbClient;
    this.configuration = configuration;
  }

  public boolean run() throws Exception {
    List<BackupDirectory> backupDirectories = findBackupDirectories();
    if (params.isFailOnBadDirectories()) {
      if (!checkDirectories(backupDirectories)) {
        return false;
      }
      logger.debug("All configured directories seem to be accessible");
    }

    BackupAgentContext ctx = new BackupAgentContext(dbClient, backupDirectories, configuration);
    boolean success = run(ctx);

    return success;
  }

  private boolean run(BackupAgentContext ctx) {
    executionLatch.start();
    PidFileWriter pidFileWriter = null;
    if (params.getPidFile() != null) {
      pidFileWriter = new PidFileWriter(params.getPidFile(), executionLatch);
      if (!pidFileWriter.start()) {
        return false;
      }
    }

    long t0 = System.currentTimeMillis();
    ScheduledBackupTask backupTask = new ScheduledBackupTask(dbClient, configuration, !params.isRunOnce(), executionLatch, () -> executeJob(ctx.backupDirectories));
    backupTask.scheduleTask(params.isForceBackupNow());

    executionLatch.joinUninterruptibly();
    backupTask.shutdown();
    if (pidFileWriter != null) {
      pidFileWriter.finish();
    }

    logger.info("Executed {} backups in {}", backupTask.getExecutionCount(), TimeUtils.formatMillis(System.currentTimeMillis() - t0));
    return true;
  }

  private void executeJob(List<BackupDirectory> backupDirectories) {
    BackupReport report = new BackupReport();
    report.setStartedAt(ZonedDateTime.now());

    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    report.setFinishedAt(ZonedDateTime.now());
    System.out.println("----");
    System.out.println(report);
    System.out.println("----");
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
    Map<File, ConfiguredDirectory> savedDirectories = dbClient.buildQuery("select directory_id, directory from directory")
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

    dbClient.buildQuery("insert into directory (directory_id, directory) values (?, ?)")
            .executeUpdate(newDirectories, (s, v) -> {
              v.uuidValue(1, s.getId());
              v.fileValue(2, s.getConfiguration().getDirectory());
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

  class BackupAgentContext {
    final DbClient dbClient;
    final List<BackupDirectory> backupDirectories;
    final Configuration configuration;

    private BackupAgentContext(DbClient dbClient, List<BackupDirectory> backupDirectories, Configuration configuration) {
      this.dbClient = dbClient;
      this.backupDirectories = backupDirectories;
      this.configuration = configuration;
    }
  }
}
