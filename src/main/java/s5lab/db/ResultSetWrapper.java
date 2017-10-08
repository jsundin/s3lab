package s5lab.db;

import s4lab.TimeUtils;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

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
    return ts == null ? null : TimeUtils.at(ts, ZoneOffset.UTC).toZonedDateTime(ZoneId.systemDefault());
  }

  public File getFile(int columnIndex) throws SQLException {
    String file = getString(columnIndex);
    return file == null ? null : new File(file);
  }

  public UUID getUuid(int columnIndex) throws SQLException {
    String uuid = getString(columnIndex);
    return uuid == null ? null : UUID.fromString(uuid);
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
    return ts == null ? null : TimeUtils.at(ts, ZoneOffset.UTC).toZonedDateTime(ZoneId.systemDefault());
  }

  public File getFile(String columnLabel) throws SQLException {
    String file = getString(columnLabel);
    return file == null ? null : new File(file);
  }

  public UUID getUuid(String columnLabel) throws SQLException {
    String uuid = getString(columnLabel);
    return uuid == null ? null : UUID.fromString(uuid);
  }
}
