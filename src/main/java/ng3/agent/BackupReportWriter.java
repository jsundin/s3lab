package ng3.agent;

import s4lab.TimeUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class BackupReportWriter implements BackupReport {
  private List<String> warnings = new ArrayList<>();
  private List<String> errors = new ArrayList<>();
  private ZonedDateTime startedAt;
  private ZonedDateTime finishedAt;
  private final FileScannerReportWriter fileScannerReport = new FileScannerReportWriter();

  public FileScannerReport getFileScannerReport() {
    return fileScannerReport;
  }

  public FileScannerReportWriter getFileScannerReportWriter() {
    return fileScannerReport;
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
            .append("Execution time: ").append(TimeUtils.formatMillis(ChronoUnit.MILLIS.between(startedAt, finishedAt)));
    sb.append("\n");
    sb.append("Errors:\n");
    for (String error : errors) {
      sb.append(error).append("\n");
    }
    sb.append("Warnings:\n");
    for (String warning : warnings) {
      sb.append(warning).append("\n");
    }
    sb.append("FileScanner stats: [").append(fileScannerReport.toString()).append("]");
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

    public void newFile() {
      newFiles++;
    }

    public void updatedFile() {
      updatedFiles++;
    }

    public void foundFile() {
      foundFiles++;
    }

    public void rejectedFile() {
      rejectedFiles++;
    }

    public void acceptedFile() {
      acceptedFiles++;
    }

    public void acceptedDirectory() {
      acceptedDirectories++;
    }

    public void deletedFile() {
      deletedFiles++;
    }

    @Override
    public String toString() {
      return "time=" + TimeUtils.formatMillis(ChronoUnit.MILLIS.between(startedAt, finishedAt)) + ", found=" + foundFiles + ", rejected=" + rejectedFiles + ", acceptedFiles=" + acceptedFiles + ", acceptedDirs=" + acceptedDirectories + ", newFiles=" + newFiles + ", updatedFiles=" + updatedFiles + ", deletedFiles=" + deletedFiles;
    }
  }
}
