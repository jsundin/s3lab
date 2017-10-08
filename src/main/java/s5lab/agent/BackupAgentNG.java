package s5lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.BackupJob;
import s5lab.configuration.Configuration;
import s5lab.configuration.ConfigurationReader;
import s5lab.configuration.JobConfiguration;
import s5lab.configuration.RetentionPolicy;
import s5lab.db.DbClient;
import s5lab.db.DbHandler;
import s5lab.notification.Notification;
import s5lab.notification.NotificationService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BackupAgentNG {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public void run(String... args) {
    Configuration conf;
    try {
      conf = new ConfigurationReader().read(new File("/data/projects/s3lab/src/main/resources/work-conf.json"), ConfigurationReader.Format.JSON);
    } catch (IOException e) {
      logger.error("Could not read configuration", e);
      return;
    }

    NotificationService notificationService = new NotificationService(conf.getNotificationProviders());

    DbHandler dbHandler = new DbHandler(conf.getDatabaseConfiguration());
    List<BackupJob> jobs;
    try {
      if (!dbHandler.isInstalled()) {
        dbHandler.install();
      }
      jobs = findBackupJobs(dbHandler.getClient(), conf.getJobConfigurations());
    } catch (Throwable t) {
      logger.error("BackupAgent failed to start", t);
      notificationService.notify(notificationService.newNotification()
              .withPriority(Notification.Priority.HIGH)
              .withShortMessage("Backup failed during startup")
              .withException(t)
              .build());
      return;
    }

    BackupAgentContext ctx = new BackupAgentContext(dbHandler.getClient(), notificationService);
    try {
      runAgent(ctx, conf, jobs);
    } catch (Throwable t) {
      logger.error("BackupAgent failed while working, attempting to cleanup", t);
      notificationService.notify(notificationService.newNotification()
              .withPriority(Notification.Priority.HIGH)
              .withShortMessage("Backup failed while working")
              .withException(t)
              .build());
    }

    try {
      dbHandler.close();
    } catch (Exception e) {
      logger.warn("Could not close database connection", e);
    }
  }

  private void runAgent(BackupAgentContext ctx, Configuration conf, List<BackupJob> jobs) {
  }

  private List<BackupJob> findBackupJobs(DbClient dbClient, List<JobConfiguration> configuredJobs) {
    Map<File, SavedJobConfiguration> savedJobs = dbClient.buildQuery("select id, directory, retention_policy from job")
            .executeQuery(rs -> new SavedJobConfiguration(
                    rs.getUuid("id"),
                    rs.getFile("directory"),
                    RetentionPolicy.valueOf(rs.getString("retention_policy"))
            )).stream()
            .collect(Collectors.toMap(k -> k.directory, v -> v));

    List<SavedJobConfiguration> removedFromConf = savedJobs.values().stream()
            .filter(v -> {
              for (JobConfiguration job : configuredJobs) {
                if (job.getDirectory().equals(v.directory)) {
                  return false;
                }
              }
              return true;
            })
            .collect(Collectors.toList());

    List<SavedJobConfiguration> newInConf = new ArrayList<>();
    List<SavedJobConfiguration> updatedInConf = new ArrayList<>();
    List<BackupJob> backupJobs = new ArrayList<>();

    for (JobConfiguration cj : configuredJobs) {
      if (savedJobs.containsKey(cj.getDirectory())) {
        SavedJobConfiguration savedJob = savedJobs.get(cj.getDirectory());
        if (!savedJob.retentionPolicy.equals(cj.getRetentionPolicy())) {
          savedJob.retentionPolicy = cj.getRetentionPolicy();
          updatedInConf.add(savedJob);
        }
        backupJobs.add(new BackupJob(savedJob.id, cj));
      } else {
        SavedJobConfiguration newJob = new SavedJobConfiguration(UUID.randomUUID(), cj.getDirectory(), cj.getRetentionPolicy());
        newInConf.add(newJob);
        backupJobs.add(new BackupJob(newJob.id, cj));
      }
    }

    for (SavedJobConfiguration job : removedFromConf) {
      switch (job.retentionPolicy) {
        case FAIL:
          logger.error("Job '{}' is not allowed to be removed from configuration", job.directory);
          break;

        case IGNORE:
          logger.warn("Job '{}' no longer exists in configuration and will be ignored", job.directory);
          break;

        case FORGET:
          dbClient.getRepository().deleteJob(job.id);
          logger.warn("Job '{}' was removed in configuration and has been forgotten", job.directory);
          break;

        case DELETE_BACKUPS:
          throw new UnsupportedOperationException("DELETE_BACKUPS not implemented"); // TODO: implement

        default:
          throw new IllegalStateException("Invalid retention policy: " + job.retentionPolicy);
      }
    }

    int updated = dbClient.buildQuery("update job set retention_policy=? where id=?")
            .executeUpdate(updatedInConf, (v, s) -> {
              s.stringValue(1, v.retentionPolicy.toString());
              s.uuidValue(2, v.id);
            });
    if (updated > 0) {
      logger.debug("Updated {} jobs in database", updated);
    }

    int added = dbClient.buildQuery("insert into job (id, directory, retention_policy) values (?, ?, ?)")
            .executeUpdate(newInConf, (v, s) -> {
              s.uuidValue(1, v.id);
              s.fileValue(2, v.directory);
              s.stringValue(3, v.retentionPolicy.toString());
            });
    if (added > 0) {
      logger.debug("Added {} jobs to database", added);
    }

    return backupJobs;
  }

  private class SavedJobConfiguration {
    private final UUID id;
    private final File directory;
    private RetentionPolicy retentionPolicy;

    public SavedJobConfiguration(UUID id, File directory, RetentionPolicy retentionPolicy) {
      this.id = id;
      this.directory = directory;
      this.retentionPolicy = retentionPolicy;
    }
  }
}
