package s5lab;

public class BackupException extends Exception {
  public BackupException() {
    super();
  }

  public BackupException(String message) {
    super(message);
  }

  public BackupException(String message, Throwable cause) {
    super(message, cause);
  }

  public BackupException(Throwable cause) {
    super(cause);
  }

  protected BackupException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
