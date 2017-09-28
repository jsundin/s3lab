package s4lab.db;

import s4lab.TimeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

  public StateInformation readStateInformation() {
    try (Connection c = getConnection()) {
      try (Statement s = c.createStatement()) {
        try (ResultSet rs = s.executeQuery("select last_scan from state")) {
          if (!rs.next()) {
            throw new IllegalStateException("Cannot read state information");
          }
          Timestamp last_scan = rs.getTimestamp("last_scan");
          if (rs.next()) {
            throw new IllegalStateException("Too much state information");
          }

          StateInformation si = new StateInformation();
          si.setLastScan(last_scan == null ? null : TimeUtils.at(last_scan, ZoneOffset.UTC).toZonedDateTime(ZoneId.systemDefault()));
          return si;
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot read state information", e);
    }
  }

  public void setStateInformationLastScan(ZonedDateTime lastScan) {
    try (Connection c = getConnection()) {
      try (PreparedStatement s = c.prepareStatement("update state set last_scan=?")) {
        s.setTimestamp(1, lastScan == null ? null : TimeUtils.at(lastScan).toTimestamp(ZoneOffset.UTC));
        int n = s.executeUpdate();
        if (n != 1) {
          throw new IllegalStateException("State information update caused wrong number of database updates: " + n);
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not update state information", e);
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
