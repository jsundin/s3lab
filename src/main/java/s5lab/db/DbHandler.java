package s5lab.db;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.Settings;
import s5lab.configuration.DatabaseConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DbHandler implements AutoCloseable {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final HikariDataSource dataSource;
  private final DbClient dbClient = new DbClient(this);

  public DbHandler(DatabaseConfiguration configuration) {
    this(configuration.getJdbcUrl(), configuration.getUsername(), configuration.getPassword(), configuration.getMinimumPoolIdle(), configuration.getMaximumPoolSize());
  }

  public DbHandler(String jdbcUrl, String jdbcUsername, String jdbcPassword, int minimumPoolIdle, int maximumPoolSize) {
    dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(jdbcUrl);
    if (jdbcUsername != null) {
      dataSource.setUsername(jdbcUsername);
      if (jdbcPassword != null) {
        dataSource.setPassword(jdbcPassword);
      }
    }
    dataSource.setMaximumPoolSize(maximumPoolSize);
    if (minimumPoolIdle > 0) {
      dataSource.setMinimumIdle(minimumPoolIdle);
    }
    logger.debug("Connected to database '{}'", jdbcUrl);
  }

  @Override
  public void close() throws Exception {
    dataSource.close();
    logger.debug("Connection closed");
  }

  public DbClient getClient() {
    return dbClient;
  }

  public boolean isInstalled() {
    return getClient().buildQuery("select count(*) from sys.systables t join sys.sysschemas s on s.schemaid=t.schemaid where lower(s.schemaname)='app' and lower(t.tablename)='job'")
            .executeQueryForObject(rs -> rs.getInt(1)) > 0;
  }

  public boolean install() throws Exception {
    logger.info("Installing database");
    try {
      executeScript(Settings.INSTALL_SCRIPT);
      return true;
    } catch (SQLException | IOException e) {
      logger.error("Could not install database", e);
      throw e;
    }
  }

  Connection getConnection() {
    try {
      return dataSource.getConnection();
    } catch (SQLException e) {
      logger.warn("Failed to get connection", e);
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
