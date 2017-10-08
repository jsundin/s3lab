package s5lab;

import s5lab.configuration.JobConfiguration;

import java.util.UUID;

public class BackupJob {
  private final UUID id;
  private final JobConfiguration configuration;

  public BackupJob(UUID id, JobConfiguration configuration) {
    this.id = id;
    this.configuration = configuration;
  }
}
