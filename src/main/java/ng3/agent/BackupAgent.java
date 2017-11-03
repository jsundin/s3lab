package ng3.agent;

import ng3.BackupDirectory;
import ng3.common.BlockingLatch;
import ng3.common.PidFileWriter;
import ng3.conf.Configuration;
import ng3.conf.ConfigurationParser;
import ng3.conf.DirectoryConfiguration;
import ng3.db.DbClient;
import ng3.db.DbHandler;
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
  private final BlockingLatch executionLatch = new BlockingLatch("ExecutionLatch");

  public BackupAgent(BackupAgentParams params) {
    this.params = params;
  }

  public boolean run() throws Exception {
    logger.debug("Loading configuration from '{}'", params.getConfigurationFile());
    Configuration conf = new ConfigurationParser().parseConfiguration(params.getConfigurationFile());
    if (params.isOnlyTestConfiguration()) {
      logger.debug("BackupAgent instructed to only check configuration, and configuration was ok");
      return true;
    }

    DbHandler dbHandler = new DbHandler(conf.getDatabase());
    if (!dbHandler.isInstalled()) {
      logger.info("Installing database");
      dbHandler.install();
    }

    List<BackupDirectory> backupDirectories = findBackupDirectories(dbHandler.getClient(), conf.getDirectories());
    if (params.isFailOnBadDirectories()) {
      if (!checkDirectories(backupDirectories)) {
        dbHandler.close();
        return false;
      }
      logger.debug("All configured directories seem to be accessible");
    }

    BackupAgentContext ctx = new BackupAgentContext(dbHandler.getClient(), backupDirectories, conf);
    boolean success = run(ctx);

    dbHandler.close();
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
    ScheduledBackupTask task = new ScheduledBackupTask(ctx, !params.isRunOnce(), executionLatch, this::executeJob);
    task.scheduleTask(params.isForceBackupNow());

    executionLatch.joinUninterruptibly();
    task.shutdown();
    if (pidFileWriter != null) {
      pidFileWriter.finish();
    }

    logger.info("Executed {} backups in {}", task.getExecutionCount(), TimeUtils.formatMillis(System.currentTimeMillis() - t0));
    return true;
  }

  private void executeJob(BackupAgentContext ctx) {
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

  private List<BackupDirectory> findBackupDirectories(DbClient dbClient, List<DirectoryConfiguration> directories) {
    Map<File, ConfiguredDirectory> savedDirectories = dbClient.buildQuery("select directory_id, directory from directory")
            .executeQuery(rs -> new ConfiguredDirectory(
                    rs.getUuid(1),
                    rs.getFile(2)
            )).stream()
            .collect(Collectors.toMap(k -> k.directory, v -> v));

    List<ConfiguredDirectory> removedDirectories = savedDirectories.values().stream()
            .filter(v -> {
              for (DirectoryConfiguration dir : directories) {
                if (dir.getDirectory().equals(v.directory)) {
                  return false;
                }
              }
              return true;
            })
            .collect(Collectors.toList());

    List<BackupDirectory> backupDirectories = new ArrayList<>();
    List<BackupDirectory> newDirectories = new ArrayList<>();
    for (DirectoryConfiguration directory : directories) {
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
