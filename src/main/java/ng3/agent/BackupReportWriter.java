package ng3.agent;

import ng3.common.TimeUtilsNG;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackupReportWriter implements BackupReport {
  private List<String> warnings = Collections.synchronizedList(new ArrayList<>());
  private List<String> errors = Collections.synchronizedList(new ArrayList<>());
  private ZonedDateTime startedAt;
  private ZonedDateTime finishedAt;
  private final FileScannerReportWriter fileScannerReport = new FileScannerReportWriter();
  private final TargetReportWriter targetReport = new TargetReportWriter();

  public FileScannerReport getFileScannerReport() {
    return fileScannerReport;
  }

  @Override
  public TargetReport getTargetReport() {
    return targetReport;
  }

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

  public FileScannerReportWriter getFileScannerReportWriter() {
    return fileScannerReport;
  }

  public TargetReportWriter getTargetReportWriter() {
    return targetReport;
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
    sb.append("FileScanner stats: [").append(fileScannerReport.toString()).append("]").append("\n");
    sb.append("Target stats: [").append(targetReport.toString()).append("]");
    return sb.toString();
  }

  public class FileScannerReportWriter implements FileScannerReport {
    private int foundFiles;
    private int rejectedFiles;
    private int acceptedFiles;
    private int acceptedDirectories;
    private int newFiles;
    private int updatedFiles;
    private int deletedFiles;
    private ZonedDateTime startedAt;
    private ZonedDateTime finishedAt;

    public void setStartedAt(ZonedDateTime startedAt) {
      this.startedAt = startedAt;
    }

    public void setFinishedAt(ZonedDateTime finishedAt) {
      this.finishedAt = finishedAt;
    }

    public synchronized void newFile() {
      newFiles++;
    }

    public synchronized void updatedFile() {
      updatedFiles++;
    }

    public synchronized void foundFile() {
      foundFiles++;
    }

    public synchronized void rejectedFile() {
      rejectedFiles++;
    }

    public synchronized void acceptedFile() {
      acceptedFiles++;
    }

    public synchronized void acceptedDirectory() {
      acceptedDirectories++;
    }

    public synchronized void deletedFile() {
      deletedFiles++;
    }

    @Override
    public String toString() {
      return "time=" + TimeUtilsNG.formatMillis(ChronoUnit.MILLIS.between(startedAt, finishedAt)) + ", found=" + foundFiles + ", rejected=" + rejectedFiles + ", acceptedFiles=" + acceptedFiles + ", acceptedDirs=" + acceptedDirectories + ", newFiles=" + newFiles + ", updatedFiles=" + updatedFiles + ", deletedFiles=" + deletedFiles;
    }
  }

  public class TargetReportWriter implements TargetReport {
    private int processedFiles;
    private int successfulFiles;
    private int failedFiles;
    private ZonedDateTime startedAt;
    private ZonedDateTime finishedAt;

    public void setStartedAt(ZonedDateTime startedAt) {
      this.startedAt = startedAt;
    }

    public void setFinishedAt(ZonedDateTime finishedAt) {
      this.finishedAt = finishedAt;
    }

    public synchronized void processedFile() {
      processedFiles++;
    }

    public synchronized void successfulFile() {
      successfulFiles++;
    }

    public synchronized void failedFile() {
      failedFiles++;
    }

    @Override
    public String toString() {
      return "time=" + TimeUtilsNG.formatMillis(ChronoUnit.MILLIS.between(startedAt, finishedAt)) + ", processedFiles=" + processedFiles + ", successfulFiles=" + successfulFiles + ", failedFiles=" + failedFiles;
    }
  }
}
