package ng3.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ng3.drivers.BackupDriver;
import s5lab.configuration.FileRule;

import java.util.List;
import java.util.Map;

class ParsedConfiguration {
  private List<DirectoryConfiguration> directories;
  private List<FileRule> globalRules;
  private DatabaseConfiguration database;
  private int intervalInMinutes;
  private Integer versioningIntervalInMinutes;
  private BackupDriver backupDriver;
  private Map<String, String> secrets;

  public int getIntervalInMinutes() {
    return intervalInMinutes;
  }

  @JsonProperty("interval")
  @JsonDeserialize(using = IntervalToMinutesDeserializer.class)
  public void setIntervalInMinutes(int intervalInMinutes) {
    this.intervalInMinutes = intervalInMinutes;
  }

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

  public List<FileRule> getGlobalRules() {
    return globalRules;
  }

  @JsonProperty("global-rules")
  public void setGlobalRules(List<FileRule> globalRules) {
    this.globalRules = globalRules;
  }

  public BackupDriver getBackupDriver() {
    return backupDriver;
  }

  @JsonProperty("target")
  public void setBackupDriver(BackupDriver backupDriver) {
    this.backupDriver = backupDriver;
  }

  public Map<String, String> getSecrets() {
    return secrets;
  }

  public void setSecrets(Map<String, String> secrets) {
    this.secrets = secrets;
  }

  public Integer getVersioningIntervalInMinutes() {
    return versioningIntervalInMinutes;
  }

  @JsonProperty("versioning-interval")
  @JsonDeserialize(using = IntervalToMinutesDeserializer.class)
  public void setVersioningIntervalInMinutes(Integer versioningIntervalInMinutes) {
    this.versioningIntervalInMinutes = versioningIntervalInMinutes;
  }
}
