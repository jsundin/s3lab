package ng3;

import java.time.ZonedDateTime;
import java.util.UUID;

public class BackupPlan {
  private final UUID id;
  private ZonedDateTime lastStarted;

  public BackupPlan(UUID id) {
    this.id = id;
  }

  public UUID getId() {
    return id;
  }

  public ZonedDateTime getLastStarted() {
    return lastStarted;
  }

  public void setLastStarted(ZonedDateTime lastStarted) {
    this.lastStarted = lastStarted;
  }
}
