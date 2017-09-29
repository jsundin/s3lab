package s4lab.db;

import s4lab.TimeUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class ResultSetWrapper {
  private final ResultSet resultSet;

  public ResultSetWrapper(ResultSet resultSet) {
    this.resultSet = resultSet;
  }

  public boolean wasNull() throws SQLException {
    return resultSet.wasNull();
  }

  public String getString(int columnIndex) throws SQLException {
    return resultSet.getString(columnIndex);
  }

  public boolean getBoolean(int columnIndex) throws SQLException {
    return resultSet.getBoolean(columnIndex);
  }

  public int getInt(int columnIndex) throws SQLException {
    return resultSet.getInt(columnIndex);
  }

  public ZonedDateTime getTimestamp(int columnIndex) throws SQLException {
    Timestamp ts = resultSet.getTimestamp(columnIndex);
    return TimeUtils.at(ts, ZoneOffset.UTC).toZonedDateTime(ZoneId.systemDefault());
  }

  public String getString(String columnLabel) throws SQLException {
    return resultSet.getString(columnLabel);
  }

  public boolean getBoolean(String columnLabel) throws SQLException {
    return resultSet.getBoolean(columnLabel);
  }

  public int getInt(String columnLabel) throws SQLException {
    return resultSet.getInt(columnLabel);
  }

  public ZonedDateTime getTimestamp(String columnLabel) throws SQLException {
    Timestamp ts = resultSet.getTimestamp(columnLabel);
    return TimeUtils.at(ts, ZoneOffset.UTC).toZonedDateTime(ZoneId.systemDefault());
  }
}
