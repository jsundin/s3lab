package ng;

import ng.conf.Configuration;
import ng.conf.ConfigurationParser;
import ng.conf.DirectoryConfiguration;
import ng.db.DbClient;
import ng.db.DbHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class BackupAgent implements Callable<Boolean> {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BackupAgentParams params;

  public BackupAgent(BackupAgentParams params) {
    this.params = params;
  }

  @Override
  public Boolean call() throws Exception {
    Configuration conf;
    try {
      conf = new ConfigurationParser().parse(params.getConfigurationFile());
    } catch (IOException e) {
      logger.error("Could not parse configuration", e);
      return false;
    }
    if (params.isTestConfigurationOnly()) {
      logger.debug("Application started with '{}' option, and configuration was ok - exiting", Main.OPT_TEST_CONFIGURATION);
      return true;
    }

    DbHandler dbHandler = new DbHandler(conf.getDatabase());
    if (!dbHandler.isInstalled()) {
      logger.info("Installing database");
      dbHandler.install();
    }

    List<BackupDirectory> directories = findBackupDirectories(dbHandler.getClient(), conf.getDirectories());
    if (!directories.isEmpty()) {

    } else {
      logger.warn("Nothing to backup");
    }

    dbHandler.finish();
    return false;
  }

  private List<BackupDirectory> findBackupDirectories(DbClient dbClient, List<DirectoryConfiguration> configuredDirectories) {
    Map<File, SavedDirectoryConfiguration> savedDirectories = dbClient.buildQuery("select directory_id, directory from directory")
        .executeQuery(rs -> new SavedDirectoryConfiguration(
            rs.getUuid(1),
            rs.getFile(2)
        )).stream()
        .collect(Collectors.toMap(k -> k.directory, v -> v));

    List<SavedDirectoryConfiguration> removedFromConf = savedDirectories.values().stream()
        .filter(v -> {
          for (DirectoryConfiguration dir : configuredDirectories) {
            if (dir.getDirectory().equals(v.directory)) {
              return false;
            }
          }
          return true;
        })
        .collect(Collectors.toList());

    List<BackupDirectory> backupDirectories = new ArrayList<>();
    List<BackupDirectory> newDirectories = new ArrayList<>();

    for (DirectoryConfiguration dir : configuredDirectories) {
      if (savedDirectories.containsKey(dir.getDirectory())) { // befintligt jobb
        SavedDirectoryConfiguration savedDir = savedDirectories.get(dir.getDirectory());
        BackupDirectory backupDirectory = new BackupDirectory(savedDir.directoryId, dir);
        backupDirectories.add(backupDirectory);
      } else { // nytt jobb
        BackupDirectory backupDirectory = new BackupDirectory(UUID.randomUUID(), dir);
        newDirectories.add(backupDirectory);
      }
    }

    dbClient.buildQuery("insert into directory (directory_id, directory) values (?, ?)")
        .executeUpdate(newDirectories, (v, s) -> {
          s.uuidValue(1, v.getId());
          s.fileValue(2, v.getConfiguration().getDirectory());
        });
    backupDirectories.addAll(newDirectories);

    logger.debug("Backing up {} directories ({} was ignored, {} was new)", backupDirectories.size(), removedFromConf.size(), newDirectories.size());
    return backupDirectories;
  }

  private class SavedDirectoryConfiguration {
    private final UUID directoryId;
    private final File directory;

    private SavedDirectoryConfiguration(UUID directoryId, File directory) {
      this.directoryId = directoryId;
      this.directory = directory;
    }
  }

  public static class BackupAgentParams {
    private File configurationFile;
    private boolean testConfigurationOnly;

    public File getConfigurationFile() {
      return configurationFile;
    }

    public void setConfigurationFile(File configurationFile) {
      this.configurationFile = configurationFile;
    }

    public boolean isTestConfigurationOnly() {
      return testConfigurationOnly;
    }

    public void setTestConfigurationOnly(boolean testConfigurationOnly) {
      this.testConfigurationOnly = testConfigurationOnly;
    }
  }
}
