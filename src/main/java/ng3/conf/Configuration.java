package ng3.conf;

import ng3.drivers.BackupDriver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Configuration {
  private final List<DirectoryConfiguration> directories;
  private final DatabaseConfiguration database;
  private final int intervalInMinutes;
  private final Integer versioningIntervalInMinutes;
  private final BackupDriver backupDriver;
  private final Map<String, char[]> secrets;

  public Configuration(List<DirectoryConfiguration> directories, DatabaseConfiguration database, int intervalInMinutes, Integer versioningIntervalInMinutes, BackupDriver backupDriver, Map<String, char[]> secrets) {
    this.directories = Collections.unmodifiableList(directories);
    this.database = database;
    this.intervalInMinutes = intervalInMinutes;
    this.versioningIntervalInMinutes = versioningIntervalInMinutes;
    this.backupDriver = backupDriver;
    this.secrets = secrets;
  }

  public List<DirectoryConfiguration> getDirectories() {
    return directories;
  }

  public DatabaseConfiguration getDatabase() {
    return database;
  }

  public int getIntervalInMinutes() {
    return intervalInMinutes;
  }

  public BackupDriver getBackupDriver() {
    return backupDriver;
  }

  public Map<String, char[]> getSecrets() {
    return secrets;
  }

  public Integer getVersioningIntervalInMinutes() {
    return versioningIntervalInMinutes;
  }
}
