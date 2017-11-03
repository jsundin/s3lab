package ng3.agent;

import java.io.File;

public class BackupAgentParams {
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
}
