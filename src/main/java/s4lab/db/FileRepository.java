package s4lab.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;

import java.io.File;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

public class FileRepository {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final DbHandler dbHandler;

  public FileRepository(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public void saveSimple(UUID directoryId, File file) throws SQLException {
    try (Connection conn = dbHandler.getConnection()) {
      String id = UUID.randomUUID().toString();

      try (PreparedStatement stmt_file = conn.prepareStatement("insert into file(id, filename, directory_id) values (?, ?, ?)")) {
        stmt_file.setString(1, id);
        stmt_file.setString(2, file.toString());
        stmt_file.setString(3, directoryId.toString());
        stmt_file.executeUpdate();
      }

      try (PreparedStatement stmt_version = conn.prepareStatement("insert into file_version(file_id, version, modified, deleted) values (?, ?, ?, ?)")) {
        stmt_version.setString(1, id);
        stmt_version.setInt(2, 0);
        stmt_version.setTimestamp(3, TimeUtils.at(file.lastModified()).toTimestamp(ZoneOffset.UTC));
        stmt_version.setBoolean(4, false);
        stmt_version.executeUpdate();
      }
    }
  }

  public tmpFileAndVersion getLatestVersionOByFilename(String filename) throws SQLException {
    try (Connection conn = dbHandler.getConnection()) {
      try (PreparedStatement stmt = conn.prepareStatement("select f.id, f.filename, v.version, v.modified, v.deleted from file f join file_version v on f.id=v.file_id where f.filename=? and v.version=(select max(version) from file_version vm where vm.file_id=f.id)")) {
        stmt.setString(1, filename);
        try (ResultSet rs = stmt.executeQuery()) {
          if (!rs.next()) {
            return null;
          }
          String _id = rs.getString(1);
          String _filename = rs.getString(2);
          int _version = rs.getInt(3);
          Timestamp _modified = rs.getTimestamp(4);
          boolean _deleted = rs.getBoolean(5);
          if (rs.next()) {
            throw new SQLException("Too many results");
          }

          tmpFileAndVersion fav = new tmpFileAndVersion();
          fav.setId(_id);
          fav.setFilename(_filename);
          fav.setVersion(_version);
          fav.setModified(TimeUtils.at(_modified, ZoneOffset.UTC).toZonedDateTime(ZoneId.systemDefault()));
          fav.setDeleted(_deleted);
          return fav;
        }
      }
    }
  }

  public void saveNewVersion(String fileId, int version, ZonedDateTime modified, boolean deleted) throws SQLException {
    try (Connection conn = dbHandler.getConnection()) {
      try (PreparedStatement stmt = conn.prepareStatement("insert into file_version (file_id, version, modified, deleted) values (?, ?, ?, ?)")) {
        stmt.setString(1, fileId);
        stmt.setInt(2, version);
        stmt.setTimestamp(3, TimeUtils.at(modified).toTimestamp(ZoneOffset.UTC));
        stmt.setBoolean(4, deleted);
        stmt.executeUpdate();
      }
      conn.commit();
    }
  }

  public class tmpFileAndVersion {
    private String id;
    private String filename;
    private int version;
    private ZonedDateTime modified;
    private boolean deleted;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    public int getVersion() {
      return version;
    }

    public void setVersion(int version) {
      this.version = version;
    }

    public ZonedDateTime getModified() {
      return modified;
    }

    public void setModified(ZonedDateTime modified) {
      this.modified = modified;
    }

    public boolean isDeleted() {
      return deleted;
    }

    public void setDeleted(boolean deleted) {
      this.deleted = deleted;
    }
  }
}
