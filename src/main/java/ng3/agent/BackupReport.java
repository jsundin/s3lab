package ng3.agent;

import s4lab.TimeUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class BackupReport {
  private ZonedDateTime startedAt;
  private ZonedDateTime finishedAt;

  public ZonedDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(ZonedDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public ZonedDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(ZonedDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder()
            .append("startedAt: ").append(startedAt)
            .append("\n")
            .append("finishedAt: ").append(finishedAt)
            .append("\n")
            .append("Execution time: ").append(TimeUtils.formatMillis(ChronoUnit.MILLIS.between(startedAt, finishedAt)));
    return sb.toString();
  }
}
