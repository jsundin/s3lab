package ng3;

import ng3.conf.DirectoryConfiguration;

import java.util.UUID;

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
