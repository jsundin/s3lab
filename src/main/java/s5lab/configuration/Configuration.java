package s5lab.configuration;

import s5lab.notification.NotificationProvider;

import java.util.Collections;
import java.util.List;

public class Configuration {
  private final List<NotificationProvider> notificationProviders;
  private final List<JobConfiguration> jobConfigurations;
  private final DatabaseConfiguration databaseConfiguration;

  public Configuration(List<NotificationProvider> notificationProviders, List<JobConfiguration> jobConfigurations, DatabaseConfiguration databaseConfiguration) {
    this.notificationProviders = Collections.unmodifiableList(notificationProviders);
    this.jobConfigurations = Collections.unmodifiableList(jobConfigurations);
    this.databaseConfiguration = databaseConfiguration;
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
}
