package s5lab.configuration;

import s5lab.Settings;
import s5lab.backuptarget.BackupProvider;
import s5lab.backuptarget.JobTargetConfiguration;

import java.io.File;

public class JobConfiguration {
  private final File directory;
  private final RetentionPolicy retentionPolicy;
  private final long interval;
  private final FileVersioningPolicy deletedFilesPolicy;
  private final FileVersioningPolicy oldVersionsPolicy;
  private final BackupProvider backupProvider;
  private final JobTargetConfiguration targetConfiguration;

  public JobConfiguration(File directory, RetentionPolicy retentionPolicy, long interval, FileVersioningPolicy deletedFilesPolicy, FileVersioningPolicy oldVersionsPolicy, BackupProvider backupProvider, JobTargetConfiguration targetConfiguration) {
    if (retentionPolicy == null) {
      throw new IllegalStateException("NULL is not allowed for retentionPolicy");
    }
    if (deletedFilesPolicy == null) {
      throw new IllegalStateException("NULL is not allowed for deletedFilesPolicy");
    }
    if (oldVersionsPolicy == null) {
      throw new IllegalStateException("NULL is not allowed for oldVersionsPolicy");
    }
    if (interval < Settings.MIN_INTERVAL) {
      throw new IllegalStateException("Interval too low: " + interval);
    }

    this.directory = directory;
    this.retentionPolicy = retentionPolicy;
    this.deletedFilesPolicy = deletedFilesPolicy;
    this.oldVersionsPolicy = oldVersionsPolicy;
    this.interval = interval;
    this.backupProvider = backupProvider;
    this.targetConfiguration = targetConfiguration;
  }

  public File getDirectory() {
    return directory;
  }

  public RetentionPolicy getRetentionPolicy() {
    return retentionPolicy;
  }

  public BackupProvider getBackupProvider() {
    return backupProvider;
  }

  public JobTargetConfiguration getTargetConfiguration() {
    return targetConfiguration;
  }

  public static class FileVersioningPolicy {
    private final FileVersioningPolicyType type;
    private final Integer keepVersions;
    private final Long expireAfterMinutes;

    public FileVersioningPolicy(FileVersioningPolicyType type) {
      this(type, null, null);
    }

    public FileVersioningPolicy(FileVersioningPolicyType type, Integer keepVersions, Long expireAfterMinutes) {
      if (type == null) {
        throw new IllegalStateException("No versioning type");
      }
      if ((type == FileVersioningPolicyType.KEEP_FOREVER || type == FileVersioningPolicyType.DELETE) && (keepVersions != null || expireAfterMinutes != null)) {
        throw new IllegalStateException("KEEP_FOREVER and DELETE do not accept expiration details");
      }
      if (type == FileVersioningPolicyType.EXPIRE && (keepVersions == null && expireAfterMinutes == null)) {
        throw new IllegalStateException("EXPIRE requires either keepVersions or expireAfter");
      }

      this.type = type;
      this.keepVersions = keepVersions;
      this.expireAfterMinutes = expireAfterMinutes;
    }
  }

  public enum FileVersioningPolicyType {
    KEEP_FOREVER,
    DELETE,
    EXPIRE
  }
}
