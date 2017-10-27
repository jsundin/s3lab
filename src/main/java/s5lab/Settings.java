package s5lab;

public class Settings {
  public static final String APP_NAME = "sbs";
  public static final String INSTALL_SCRIPT = "/s5-sql/create.sql";
  public static final String UNINSTALL_SCRIPT = "/s5-sql/drop.sql";
  public static final int MIN_INTERVAL = 10;
  public static final long FILESCANNER_POLL_TIMEOUT_IN_MS = 1000;
}
