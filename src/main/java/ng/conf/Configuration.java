package ng.conf;

import java.util.Collections;
import java.util.List;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class Configuration {
  private final List<DirectoryConfiguration> directories;
  private final DatabaseConfiguration database;

  public Configuration(List<DirectoryConfiguration> directories, DatabaseConfiguration database) {
    this.directories = Collections.unmodifiableList(directories);
    this.database = database;
  }

  public List<DirectoryConfiguration> getDirectories() {
    return directories;
  }

  public DatabaseConfiguration getDatabase() {
    return database;
  }
}
