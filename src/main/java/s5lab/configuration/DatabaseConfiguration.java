package s5lab.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DatabaseConfiguration {
  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final int minimumPoolIdle;
  private final int maximumPoolSize;

  @JsonCreator
  public DatabaseConfiguration(@JsonProperty("url") String jdbcUrl, @JsonProperty("username") String username, @JsonProperty("password") String password, @JsonProperty(value = "pool-size") String poolSizeDef) {
    if (poolSizeDef != null) {
      if (poolSizeDef.matches("^\\d+$")) {
        minimumPoolIdle = 0;
        maximumPoolSize = Integer.parseInt(poolSizeDef);
      } else if (poolSizeDef.matches("^\\d+-\\d+$")) {
        String[] split = poolSizeDef.split(poolSizeDef);
        minimumPoolIdle = Integer.parseInt(split[0]);
        maximumPoolSize = Integer.parseInt(split[1]);
      } else {
        throw new IllegalArgumentException("Invalid value for pool size");
      }
    } else {
      minimumPoolIdle = 0;
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

  public int getMinimumPoolIdle() {
    return minimumPoolIdle;
  }

  public int getMaximumPoolSize() {
    return maximumPoolSize;
  }
}
