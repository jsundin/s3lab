package ng3.agent;

import java.io.File;

public class BackupAgentParams {
  private File configurationFile;
  private boolean onlyTestConfiguration;
  private boolean failOnBadDirectories;
  private boolean forceBackupNow;
  private boolean runOnce;
  private File pidFile;

  public File getPidFile() {
    return pidFile;
  }

  public void setPidFile(File pidFile) {
    this.pidFile = pidFile;
  }

  public boolean isRunOnce() {
    return runOnce;
  }

  public void setRunOnce(boolean runOnce) {
    this.runOnce = runOnce;
  }

  public boolean isForceBackupNow() {
    return forceBackupNow;
  }

  public void setForceBackupNow(boolean forceBackupNow) {
    this.forceBackupNow = forceBackupNow;
  }

  public boolean isFailOnBadDirectories() {
    return failOnBadDirectories;
  }

  public void setFailOnBadDirectories(boolean failOnBadDirectories) {
    this.failOnBadDirectories = failOnBadDirectories;
  }

  public File getConfigurationFile() {
    return configurationFile;
  }

  public void setConfigurationFile(File configurationFile) {
    this.configurationFile = configurationFile;
  }

  public boolean isOnlyTestConfiguration() {
    return onlyTestConfiguration;
  }

  public void setOnlyTestConfiguration(boolean onlyTestConfiguration) {
    this.onlyTestConfiguration = onlyTestConfiguration;
  }
}
