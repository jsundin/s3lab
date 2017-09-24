package s4lab.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DbHandler {
  private String jdbcUrl = "jdbc:derby:files";

  public void start() {

  }

  public void finish() {

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

  Connection getConnection() throws SQLException {
    return DriverManager.getConnection(jdbcUrl);
  }
}
