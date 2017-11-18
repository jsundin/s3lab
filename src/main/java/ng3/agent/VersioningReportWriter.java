package ng3.agent;

import java.time.ZonedDateTime;
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
}
