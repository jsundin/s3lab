package s4lab.conf;

/**
 * @author johdin
 * @since 2017-09-26
 */
public final class Settings {
  public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX";
  public static final String JDBC_URL = "jdbc:derby:files;create=true"; // TODO: ska inte vara här
  public static final String JDBC_USERNAME = null;
  public static final String JDBC_PASSWORD = null;
  public static final String JDBC_POOL_SIZE = "1-1";
}
