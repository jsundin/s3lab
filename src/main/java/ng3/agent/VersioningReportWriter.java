package ng3.agent;

import ng3.common.TimeUtilsNG;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VersioningReportWriter implements VersioningReport {
  private List<String> errors = Collections.synchronizedList(new ArrayList<>());
  private List<String> warnings = Collections.synchronizedList(new ArrayList<>());
  private ZonedDateTime startedAt;
  private ZonedDateTime finishedAt;
  private int removedEmptyDirectories;
  private int removedVersions;

  @Override
  public List<String> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  @Override
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  @Override
  public ZonedDateTime getStartedAt() {
    return startedAt;
  }

  @Override
  public ZonedDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setStartedAt(ZonedDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public void setFinishedAt(ZonedDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public void addWarning(String warning, Object... args) {
    warnings.add(String.format(warning, args));
  }

  public void addError(String error, Object... args) {
    errors.add(String.format(error, args));
  }

  public synchronized void removedEmptyDirectory() {
    removedEmptyDirectories++;
  }

  public synchronized void removedVersion() {
    removedVersions++;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder()
        .append("startedAt: ").append(startedAt)
        .append("\n")
        .append("finishedAt: ").append(finishedAt)
        .append("\n")
        .append("Execution time: ").append(TimeUtilsNG.formatMillis(ChronoUnit.MILLIS.between(startedAt, finishedAt)));
    sb.append("\n");
    sb.append("Errors:\n");
    for (String error : errors) {
      sb.append(error).append("\n");
    }
    sb.append("Warnings:\n");
    for (String warning : warnings) {
      sb.append(warning).append("\n");
    }
    sb.append("Removed empty directories: ").append(removedEmptyDirectories).append("\n");
    sb.append("Removed versions: ").append(removedVersions);
    return sb.toString();
  }
}
