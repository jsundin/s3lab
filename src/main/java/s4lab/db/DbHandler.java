package s4lab.db;

import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class DbHandler {
  private final String jdbcUrl;
  private final String jdbcUsername;
  private final String jdbcPassword;
  private final String poolSize;
  private HikariDataSource ds;
  private final FileRepository fileRepository = new FileRepository(this);

  public DbHandler(String jdbcUrl, String jdbcUsername, String jdbcPassword, String poolSize) {
    this.jdbcUrl = jdbcUrl;
    this.jdbcUsername = jdbcUsername;
    this.jdbcPassword = jdbcPassword;
    this.poolSize = poolSize;
  }

  public void start() throws SQLException {
    int[] poolSz;
    if (poolSize.contains("-")) {
      String[] strPoolSz = poolSize.split("-");
      poolSz = new int[]{
              Integer.parseInt(strPoolSz[0]),
              Integer.parseInt(strPoolSz[1])
      };
    } else {
      poolSz = new int[]{Integer.parseInt(poolSize)};
    }

    ds = new HikariDataSource();
    ds.setJdbcUrl(jdbcUrl);
    if (jdbcUsername != null) {
      ds.setUsername(jdbcUsername);
      if (jdbcPassword != null) {
        ds.setPassword(jdbcPassword);
      }
    }
    ds.setMaximumPoolSize(poolSz.length == 1 ? poolSz[0] : poolSz[1]);
    if (poolSz.length == 2) {
      ds.setMinimumIdle(poolSz[0]);
    }
  }

  public void finish() throws SQLException {
    ds.close();
  }

  public void dropDatabase() throws SQLException, IOException {
    executeScript("/sql/drop.sql");
  }

  public void createDatabase() throws SQLException, IOException {
    executeScript("/sql/create.sql");
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

  public Connection getConnection() throws SQLException {
    return ds.getConnection();
  }

  public QueryBuilder buildQuery(String sql) {
    try {
      return new QueryBuilder(getConnection()).withStatement(sql);
    } catch (Throwable t) {
      throw new DatabaseException(t);
    }
  }

  public FileRepository getFileRepository() {
    return fileRepository;
  }

  @Deprecated
  public StateInformation readStateInformation() {
    StateInformation stateInformation = buildQuery("select last_scan from state")
            .executeQueryForObject(rs -> {
              StateInformation si = new StateInformation();
              si.setLastScan(rs.getTimestamp(1));
              return si;
            });
    if (stateInformation == null) {
      throw new IllegalStateException("No state information found");
    }
    return stateInformation;
  }

  @Deprecated
  public void setStateInformationLastScan(ZonedDateTime lastScan) {
    int n = buildQuery("update state set last_scan=?")
            .withParam().timestampValue(1, lastScan)
            .executeUpdate();
    if (n != 1) {
      throw new IllegalStateException("State information not updated properly, expected 1 but updated " + n);
    }
  }

  @Deprecated
  public class StateInformation {
    private ZonedDateTime lastScan;

    public ZonedDateTime getLastScan() {
      return lastScan;
    }

    public void setLastScan(ZonedDateTime lastScan) {
      this.lastScan = lastScan;
    }
  }
}
