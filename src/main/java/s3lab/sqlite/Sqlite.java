package s3lab.sqlite;

import java.sql.*;

public class Sqlite {
  public static Connection getConnection(String url) throws SQLException {
    return DriverManager.getConnection(url);
  }

  public static void main(String[] args) throws Exception {
    Connection conn = DriverManager.getConnection("jdbc:sqlite:test.db");
    Statement stmt = conn.createStatement();
    stmt.executeUpdate("drop table if exists somedata");
    stmt.executeUpdate("create table somedata (id integer, value varchar(255))");
    stmt.executeUpdate("insert into somedata values(1, 'v1')");
    stmt.executeUpdate("insert into somedata values(2, 'v2')");
    ResultSet rs = stmt.executeQuery("select * from somedata");
    while (rs.next()) {
      System.out.println(rs.getInt("id") + ": " + rs.getString("value"));
    }
    conn.close();
  }
}
