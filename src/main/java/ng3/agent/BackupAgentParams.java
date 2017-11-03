package ng3.agent;

public class BackupAgentParams {
  private boolean failOnBadDirectories;
  private boolean forceBackupNow;
  private boolean runOnce;

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
