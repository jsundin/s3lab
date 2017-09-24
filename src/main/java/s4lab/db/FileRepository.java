package s4lab.db;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static s3lab.Utils.longToLDT;

public class FileRepository {
  private final DbHandler dbHandler;

  public FileRepository(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public int deleteAndInsertFiles(List<File> files) throws SQLException {
    int inserts = 0;
    int n = 0;
    try (Connection conn = dbHandler.getConnection()) {
      try (PreparedStatement stmt = conn.prepareStatement("delete from file")) {
        stmt.executeUpdate();
      }
      try (PreparedStatement stmt = conn.prepareStatement("insert into file(id, filename) values(?, ?)")) {
        for (File file : files) {
          stmt.setString(1, UUID.randomUUID().toString());
          stmt.setString(2, file.toString());
          inserts += stmt.executeUpdate();
          n++;
          if (n % 100 == 0) System.out.println(n + "/" + files.size());
        }
      }
    }
    return inserts;
  }

  public void saveSimple(File file) throws SQLException {
    try (Connection conn = dbHandler.getConnection()) {
      String id = UUID.randomUUID().toString();

      try (PreparedStatement stmt_file = conn.prepareStatement("insert into file(id, filename) values (?, ?)")) {
        stmt_file.setString(1, id);
        stmt_file.setString(2, file.toString());
        stmt_file.executeUpdate();
      }

      try (PreparedStatement stmt_version = conn.prepareStatement("insert into file_version(id, file_id, version, modified, deleted) values (?, ?, ?, ?, ?)")) {
        stmt_version.setString(1, UUID.randomUUID().toString());
        stmt_version.setString(2, id);
        stmt_version.setInt(3, 0);
        stmt_version.setTimestamp(4, Timestamp.valueOf(longToLDT(file.lastModified())));
        stmt_version.setBoolean(5, false);
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
          fav.setModified(_modified.toLocalDateTime());
          fav.setDeleted(_deleted);
          return fav;
        }
      }
    }
  }

  public void saveNewVersion(String fileId, int version, LocalDateTime modified, boolean deleted) throws SQLException {
    try (Connection conn = dbHandler.getConnection()) {
      try (PreparedStatement stmt = conn.prepareStatement("insert into file_version (id, file_id, version, modified, deleted) values (?, ?, ?, ?, ?)")) {
        stmt.setString(1, UUID.randomUUID().toString());
        stmt.setString(2, fileId);
        stmt.setInt(3, version);
        stmt.setTimestamp(4, Timestamp.valueOf(modified));
        stmt.setBoolean(5, deleted);
        stmt.executeUpdate();
      }
    }
  }

  public class tmpFileAndVersion {
    private String id;
    private String filename;
    private int version;
    private LocalDateTime modified;
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

    public LocalDateTime getModified() {
      return modified;
    }

    public void setModified(LocalDateTime modified) {
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
