package ng3;

import java.time.ZonedDateTime;

public class BackupState {
  private ZonedDateTime lastStarted;

  public BackupState(ZonedDateTime lastStarted) {
    this.lastStarted = lastStarted;
  }

  public void setLastStarted(ZonedDateTime lastStarted) {
    this.lastStarted = lastStarted;
  }

  public ZonedDateTime getLastStarted() {
    return lastStarted;
  }
}
