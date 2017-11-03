package ng.db;

import com.zaxxer.hikari.HikariDataSource;
import ng.Settings;
import ng.conf.DatabaseConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.db.DatabaseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class DbHandler {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final HikariDataSource dataSource;
  private final DbClient dbClient;

  public DbHandler(DatabaseConfiguration configuration) {
    this(configuration.getJdbcUrl(), configuration.getUsername(), configuration.getPassword(), configuration.getMinimumPoolSizeIdle(), configuration.getMaximumPoolSize());
  }

  public DbHandler(String jdbcUrl, String username, String password, int minimumPoolSizeIdle, int maximumPoolSize) {
    dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(jdbcUrl);
    if (username != null) {
      dataSource.setUsername(username);
    }
    if (password != null) {
      dataSource.setPassword(password);
    }
    dataSource.setMaximumPoolSize(maximumPoolSize);
    if (minimumPoolSizeIdle > 0) {
      dataSource.setMinimumIdle(minimumPoolSizeIdle);
    }
    logger.debug("Connected to database '{}'", jdbcUrl);
    dbClient = new DbClient(this);
  }

  public void finish() {
    dataSource.close();
    logger.debug("Disconnected from database");
  }

  public DbClient getClient() {
    return dbClient;
  }

  public boolean isInstalled() {
    return getClient().buildQuery("select count(*) from sys.systables t join sys.sysschemas s on s.schemaid=t.schemaid where lower(s.schemaname)='app' and lower(t.tablename)='directory'")
        .executeQueryForObject(rs -> rs.getInt(1) > 0);
  }

  public void install() throws Exception {
    executeScript(Settings.CREATE_SCRIPT);
  }

  public void uninstall() throws Exception {
    executeScript(Settings.DROP_SCRIPT);
  }

  Connection getConnection() {
    try {
      return dataSource.getConnection();
    } catch (SQLException e) {
      logger.error("Failed to get connection from connection pool", e);
      throw new DatabaseException(e);
    }
  }

  private void executeScript(String resource) throws SQLException, IOException {
    try (Connection c = getConnection()) {
      List<String> sqls = readSqlScript(resource);
      try (Statement s = c.createStatement()) {
        for (String sql : sqls) {
          s.execute(sql);
        }
      }
      c.commit();
    }
  }

  private List<String> readSqlScript(String resource) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(resource)) {
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String line;
      StringBuffer sb = new StringBuffer();
      List<String> sqls = new ArrayList<>();
      while ((line = br.readLine()) != null) {
        sb.append(line);
        if (line.endsWith(";")) {
          String sql = sb.toString();
          sqls.add(sql.substring(0, sql.length() - 1));
          sb = new StringBuffer();
        }
      }
      return sqls;
    }
  }
}
