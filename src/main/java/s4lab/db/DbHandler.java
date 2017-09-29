package s4lab.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class DbHandler {
  private String jdbcUrl = "jdbc:derby:files;create=true";
  //private String jdbcUrl = "jdbc:hsqldb:file:/tmp/hdb/files";
  private Connection actualConnection;

  public void start() throws SQLException {
    if (actualConnection != null) {
      throw new IllegalStateException("DbHandler already started");
    }
    actualConnection = DriverManager.getConnection(jdbcUrl);//, "SA", "");
  }

  public void finish() throws SQLException {
    if (actualConnection == null) {
      throw new IllegalStateException("DbHandler not running");
    }
    actualConnection.close();
    actualConnection = null;
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
    return new ConnectionProxy(actualConnection);
  }

  public QueryBuilder buildQuery(String sql) {
    try {
      return new QueryBuilder(getConnection()).withStatement(sql);
    } catch (Throwable t) {
      throw new DatabaseException(t);
    }
  }

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

  public void setStateInformationLastScan(ZonedDateTime lastScan) {
    int n = buildQuery("update state set last_scan=?")
            .withParam().timestampValue(1, lastScan)
            .executeUpdate();
    if (n != 1) {
      throw new IllegalStateException("State information not updated properly, expected 1 but updated " + n);
    }
  }

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
