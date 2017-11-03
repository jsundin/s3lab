package ng.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class DatabaseConfiguration {
  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final int minimumPoolSizeIdle;
  private final int maximumPoolSize;

  @JsonCreator
  public DatabaseConfiguration(
      @JsonProperty("url") String jdbcUrl,
      @JsonProperty("username") String username,
      @JsonProperty("password") String password,
      @JsonProperty("pool-size") String poolSize) {
    if (poolSize != null) {
      if (poolSize.matches("^\\d+$")) {
        minimumPoolSizeIdle = 0;
        maximumPoolSize = Integer.parseInt(poolSize);
      } else if (poolSize.matches("^\\d+-\\d+$")) {
        String[] split = poolSize.split(poolSize);
        minimumPoolSizeIdle = Integer.parseInt(split[0]);
        maximumPoolSize = Integer.parseInt(split[1]);
      } else {
        throw new IllegalArgumentException("Invalid value for pool size");
      }
    } else {
      minimumPoolSizeIdle = 0;
      maximumPoolSize = 1;
    }

    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
  }

  public String getJdbcUrl() {
    return jdbcUrl;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public int getMinimumPoolSizeIdle() {
    return minimumPoolSizeIdle;
  }

  public int getMaximumPoolSize() {
    return maximumPoolSize;
  }
}