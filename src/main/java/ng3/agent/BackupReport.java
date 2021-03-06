package ng3.agent;

import java.time.ZonedDateTime;
import java.util.List;

public interface BackupReport {
  FileScannerReport getFileScannerReport();
  TargetReport getTargetReport();
  List<String> getErrors();
  List<String> getWarnings();
  ZonedDateTime getStartedAt();
  ZonedDateTime getFinishedAt();

  interface FileScannerReport {

  }

  interface TargetReport {

  }
}
