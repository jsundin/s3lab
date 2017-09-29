package s3lab.filemon2;

import java.net.URI;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbAdapter {
  private static final String JDBC_URL = "jdbc:sqlite:files.db";

  public void initTables() throws SQLException {
    try (Connection conn = getConnection(JDBC_URL)) {
      try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("drop table if exists files");
        stmt.executeUpdate("create table files (path text, last_modified timestamp, last_pushed timestamp, deleted_at timestamp, version integer)");
        stmt.close();
      }
    }
  }

  public Map<URI, FileDefinition> findAllFiles() throws SQLException {
    Map<URI, FileDefinition> files = new HashMap<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      try (Statement stmt = conn.createStatement()) {
        try (ResultSet rs = stmt.executeQuery("select path, last_modified, last_pushed, deleted_at, version from files")) {
          while (rs.next()) {
            FileDefinition fd = mapFileDefinition(rs);
            files.put(fd.getFile().toUri(), fd);
          }
        }
      }
    }

    return files;
  }

  public List<FileDefinition> findOldDeletedFiles(LocalDateTime maxAge) throws SQLException {
    List<FileDefinition> fileDefinitions = new ArrayList<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("select path, last_modified, last_pushed, deleted_at, version from files where deleted_at is not null and deleted_at<=?")) {
        stmt.setTimestamp(1, Timestamp.valueOf(maxAge));
        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            fileDefinitions.add(mapFileDefinition(rs));
          }
        }
      }
    }
    return fileDefinitions;
  }

  public List<FileDefinition> findFiles(List<URI> uris) throws SQLException {
    List<FileDefinition> fileDefinitions = new ArrayList<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("select path, last_modified, last_pushed, deleted_at, version from files where path=?")) {
        for (URI uri : uris) {
          stmt.setString(1, uri.toString());
          try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
              FileDefinition result = mapFileDefinition(rs);
              if (rs.next()) {
                throw new RuntimeException("Too many results"); // TODO: hantera bättre
              }
              fileDefinitions.add(result);
            }
          }
        }
      }
    }
    return fileDefinitions;
  }

  public int addFiles(List<FileDefinition> fds) throws SQLException {
    int inserts = 0;
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("insert into files (path, last_modified, last_pushed, deleted_at, version) values(?, ?, ?, ?, ?)")) {
        for (FileDefinition fileDefinition : fds) {
          stmt.setString(1, fileDefinition.getFile().toUri().toString());
          stmt.setTimestamp(2, Timestamp.valueOf(fileDefinition.getLastModified()));
          stmt.setTimestamp(3, fileDefinition.getLastPushed() == null ? null : Timestamp.valueOf(fileDefinition.getLastPushed()));
          stmt.setTimestamp(4, fileDefinition.getDeletedAt() == null ? null : Timestamp.valueOf(fileDefinition.getDeletedAt()));
          stmt.setInt(5, fileDefinition.getVersion());
          inserts += stmt.executeUpdate();
        }
      }
    }
    return inserts;
  }

  public int updateFiles(List<FileDefinition> fds) throws SQLException {
    int updates = 0;
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("executeUpdate files set last_modified=?, last_pushed=?, deleted_at=?, version=? where path=?")) {
        for (FileDefinition fd : fds) {
          stmt.setTimestamp(1, Timestamp.valueOf(fd.getLastModified()));
          stmt.setTimestamp(2, fd.getLastPushed() == null ? null : Timestamp.valueOf(fd.getLastPushed()));
          stmt.setTimestamp(3, fd.getDeletedAt() == null ? null : Timestamp.valueOf(fd.getDeletedAt()));
          stmt.setInt(4, fd.getVersion());
          stmt.setString(5, fd.getFile().toUri().toString());
          updates += stmt.executeUpdate();
        }
      }
    }
    return updates;
  }

  private FileDefinition mapFileDefinition(ResultSet rs) throws SQLException {
    String path = rs.getString("path");
    Timestamp last_modified = rs.getTimestamp("last_modified");
    Timestamp last_pushed = rs.getTimestamp("last_pushed");
    Timestamp deleted_at = rs.getTimestamp("deleted_at");
    int version = rs.getInt("version");

    FileDefinition fd = new FileDefinition();
    fd.setFile(Paths.get(URI.create(path)));
    fd.setLastModified(last_modified == null ? null : last_modified.toLocalDateTime());
    fd.setLastPushed(last_pushed == null ? null : last_pushed.toLocalDateTime());
    fd.setDeletedAt(deleted_at == null ? null : deleted_at.toLocalDateTime());
    fd.setVersion(version);

    return fd;
  }

  private Connection getConnection(String url) throws SQLException {
    return DriverManager.getConnection(url);
  }
}
