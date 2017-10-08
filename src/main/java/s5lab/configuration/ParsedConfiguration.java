package s5lab.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import s5lab.notification.NotificationProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;

class ParsedConfiguration {
  private List<NotificationProvider> notificationProviders;
  private List<JobConf> jobs;
  private DatabaseConfiguration database;

  public DatabaseConfiguration getDatabase() {
    return database;
  }

  public void setDatabase(DatabaseConfiguration database) {
    this.database = database;
  }

  public List<JobConf> getJobs() {
    return jobs;
  }

  public void setJobs(List<JobConf> jobs) {
    this.jobs = jobs;
  }

  public List<NotificationProvider> getNotificationProviders() {
    return notificationProviders;
  }

  @JsonProperty("notifications")
  public void setNotificationProviders(List<NotificationProvider> notificationProviders) {
    this.notificationProviders = notificationProviders;
  }

  static class JobConf {
    private File directory;
    private RetentionPolicy retentionPolicy;
    private Long intervalInMinutes;
    private JobDeletedFilesPolicy deletedFilesPolicy;
    private JobOldVersionsPolicy oldVersionsPolicy;

    public Long getIntervalInMinutes() {
      return intervalInMinutes;
    }

    @JsonDeserialize(using = IntervalToMinutesDeserializer.class)
    @JsonProperty("interval")
    public void setIntervalInMinutes(Long intervalInMinutes) {
      this.intervalInMinutes = intervalInMinutes;
    }

    public JobDeletedFilesPolicy getDeletedFilesPolicy() {
      return deletedFilesPolicy;
    }

    @JsonProperty("deleted-files-policy")
    public void setDeletedFilesPolicy(JobDeletedFilesPolicy deletedFilesPolicy) {
      this.deletedFilesPolicy = deletedFilesPolicy;
    }

    public JobOldVersionsPolicy getOldVersionsPolicy() {
      return oldVersionsPolicy;
    }

    @JsonProperty("old-versions-policy")
    public void setOldVersionsPolicy(JobOldVersionsPolicy oldVersionsPolicy) {
      this.oldVersionsPolicy = oldVersionsPolicy;
    }

    public RetentionPolicy getRetentionPolicy() {
      return retentionPolicy;
    }

    @JsonProperty("retention-policy")
    public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
      this.retentionPolicy = retentionPolicy;
    }

    public File getDirectory() {
      return directory;
    }

    @JsonDeserialize(using = StringToFileDeserializer.class)
    public void setDirectory(File directory) {
      this.directory = directory;
    }
  }

  static class JobDeletedFilesPolicy {
    private Boolean keepForever;
    private Boolean delete;
    private Integer keepVersions;
    private Long expireAfterMinutes;

    public Boolean getKeepForever() {
      return keepForever;
    }

    @JsonProperty("keep-forever")
    public void setKeepForever(Boolean keepForever) {
      this.keepForever = keepForever;
    }

    public Boolean getDelete() {
      return delete;
    }

    public void setDelete(Boolean delete) {
      this.delete = delete;
    }

    public Integer getKeepVersions() {
      return keepVersions;
    }

    @JsonProperty("keep-versions")
    public void setKeepVersions(Integer keepVersions) {
      this.keepVersions = keepVersions;
    }

    public Long getExpireAfterMinutes() {
      return expireAfterMinutes;
    }

    @JsonProperty("expire-after")
    @JsonDeserialize(using = IntervalToMinutesDeserializer.class)
    public void setExpireAfterMinutes(Long expireAfter) {
      this.expireAfterMinutes = expireAfter;
    }
  }

  static class JobOldVersionsPolicy {
    private Boolean keepForever;
    private Boolean deleteOldVersions;
    private Integer keepOldVersions;
    private Long expireAfterMinutes;

    public Boolean getKeepForever() {
      return keepForever;
    }

    @JsonProperty("keep-forever")
    public void setKeepForever(Boolean keepForever) {
      this.keepForever = keepForever;
    }

    public Boolean getDeleteOldVersions() {
      return deleteOldVersions;
    }

    @JsonProperty("delete-old-versions")
    public void setDeleteOldVersions(Boolean deleteOldVersions) {
      this.deleteOldVersions = deleteOldVersions;
    }

    public Integer getKeepOldVersions() {
      return keepOldVersions;
    }

    @JsonProperty("keep-old-versions")
    public void setKeepOldVersions(Integer keepOldVersions) {
      this.keepOldVersions = keepOldVersions;
    }

    public Long getExpireAfterMinutes() {
      return expireAfterMinutes;
    }

    @JsonProperty("expire-after")
    @JsonDeserialize(using = IntervalToMinutesDeserializer.class)
    public void setExpireAfterMinutes(Long expireAfter) {
      this.expireAfterMinutes = expireAfter;
    }
  }

  static class StringToFileDeserializer extends JsonDeserializer<File> {
    @Override
    public File deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      String fileStr = jsonParser.getText();
      return new File(fileStr);
    }
  }

  static class IntervalToMinutesDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      String interval = jsonParser.getText();
      if (interval == null || interval.length() < 2) {
        throw new IOException("Invalid interval: '" + interval + "'");
      }

      char lastChar = interval.charAt(interval.length() - 1);
      int val = Integer.parseInt(interval.substring(0, interval.length() - 1));
      long result;

      switch (lastChar) {
        case 'm':
          result = val;
          break;

        case 'h':
          result = val * 60L;
          break;

        case 'd':
          result = val * 24L * 60L;
          break;

        case 'w':
          result = val * 7L * 24L * 60L;
          break;

        case 'y':
          result = val * 366 * 24L * 60L;
          break;

        default:
          throw new IOException("Could not parse interval '" + interval + "'");
      }

      return result;
    }
  }
}
