package s5lab.configuration;

import s4lab.agent.backuptarget.BackupTarget;
import s5lab.backuptarget.BackupProvider;
import s5lab.notification.NotificationProvider;

import java.util.Collections;
import java.util.List;

public class Configuration {
  private final List<NotificationProvider> notificationProviders;
  private final List<JobConfiguration> jobConfigurations;
  private final DatabaseConfiguration databaseConfiguration;
  private final List<BackupProvider> backupProviders;

  public Configuration(List<NotificationProvider> notificationProviders, List<JobConfiguration> jobConfigurations, DatabaseConfiguration databaseConfiguration, List<BackupProvider> backupProviders) {
    this.notificationProviders = Collections.unmodifiableList(notificationProviders);
    this.jobConfigurations = Collections.unmodifiableList(jobConfigurations);
    this.databaseConfiguration = databaseConfiguration;
    this.backupProviders = Collections.unmodifiableList(backupProviders);
  }

  public List<NotificationProvider> getNotificationProviders() {
    return notificationProviders;
  }

  public DatabaseConfiguration getDatabaseConfiguration() {
    return databaseConfiguration;
  }

  public List<JobConfiguration> getJobConfigurations() {
    return jobConfigurations;
  }

  public List<BackupProvider> getBackupProviders() {
    return backupProviders;
  }
}
