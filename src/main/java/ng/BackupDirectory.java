package ng;

import ng.conf.DirectoryConfiguration;

import java.util.UUID;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class BackupDirectory {
  private final UUID id;
  private final DirectoryConfiguration configuration;

  public BackupDirectory(UUID id, DirectoryConfiguration configuration) {
    this.id = id;
    this.configuration = configuration;
  }

  public UUID getId() {
    return id;
  }

  public DirectoryConfiguration getConfiguration() {
    return configuration;
  }
}
