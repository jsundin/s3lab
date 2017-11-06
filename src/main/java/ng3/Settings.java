package ng3;

public class Settings {
  public static final String CREATE_SCRIPT = "/ng3/create.sql";
  public static final String DROP_SCRIPT = "/ng3/drop.sql";
  public static final long FILESCAN_POLL_TIMEOUT_IN_MS = 200;
  public static final long BACKUP_DRIVER_POLL_TIMEOUT_IN_MS = 200;

  public static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
  public static final int KEY_ITERATIONS = 6000;
  public static final int KEY_LENGTH = 128;
  public static final int KEY_SALT_LENGTH = KEY_LENGTH / 8;
  public static final String CIPHER_TRANSFORMATION = "AES/CFB128/PKCS5Padding";
  public static final int IV_LENGTH = 16;
}
