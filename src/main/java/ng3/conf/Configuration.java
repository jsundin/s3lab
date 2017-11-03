package ng3.conf;

import java.util.Collections;
import java.util.List;

public class Configuration {
  private final List<DirectoryConfiguration> directories;
  private final DatabaseConfiguration database;
  private final int intervalInMinutes;

  public Configuration(List<DirectoryConfiguration> directories, DatabaseConfiguration database, int intervalInMinutes) {
    this.directories = Collections.unmodifiableList(directories);
    this.database = database;
    this.intervalInMinutes = intervalInMinutes;
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
}
