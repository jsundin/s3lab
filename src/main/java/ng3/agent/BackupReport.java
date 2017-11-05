package ng3.agent;

public interface BackupReport {
  FileScannerReport getFileScannerReport();
  TargetReport getTargetReport();

  interface FileScannerReport {

  }

  interface TargetReport {

  }
}
