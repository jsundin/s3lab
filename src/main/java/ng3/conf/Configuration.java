package ng3.conf;

import ng3.drivers.BackupDriver;

import java.util.Collections;
import java.util.List;

public class Configuration {
  private final List<DirectoryConfiguration> directories;
  private final DatabaseConfiguration database;
  private final int intervalInMinutes;
  private final BackupDriver backupDriver;

  public Configuration(List<DirectoryConfiguration> directories, DatabaseConfiguration database, int intervalInMinutes, BackupDriver backupDriver) {
    this.directories = Collections.unmodifiableList(directories);
    this.database = database;
    this.intervalInMinutes = intervalInMinutes;
    this.backupDriver = backupDriver;
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
}
