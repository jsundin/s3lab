package ng.conf;

import java.util.List;

/**
 * @author johdin
 * @since 2017-11-03
 */
class ParsedConf {
  private List<DirectoryConfiguration> directories;
  private DatabaseConfiguration database;

  public DatabaseConfiguration getDatabase() {
    return database;
  }

  public void setDatabase(DatabaseConfiguration database) {
    this.database = database;
  }

  public List<DirectoryConfiguration> getDirectories() {
    return directories;
  }

  public void setDirectories(List<DirectoryConfiguration> directories) {
    this.directories = directories;
  }
}
