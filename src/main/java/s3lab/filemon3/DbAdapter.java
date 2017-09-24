package s3lab.filemon3;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static s3lab.Utils.getFilename;
import static s3lab.Utils.getPath;

public class DbAdapter {
  //private static final String JDBC_URL = "jdbc:sqlite:files2.db";
  //private static final String JDBC_URL = "jdbc:derby:files2";
  //private static final String JDBC_URL = "jdbc:derby://localhost:1527/tcp_files";
  private static final String JDBC_URL = "jdbc:h2:~/files";
  private static final String FILES_TABLE = "files";
  private static final String[] FILES_COLUMNS = new String[]{
          "filename",
          "version",
          "last_modified",
          "deleted"
  };

  public void start() throws SQLException{
    /*
    try (Connection conn = getConnection(JDBC_URL + ";create=true")) {

    }
    */
  }

  public void finish() throws SQLException {
    /*
    try (Connection conn = getConnection(JDBC_URL + ";shutdown=true")) {

    }
    */
  }

  public void initTables() throws SQLException {
    try (Connection conn = getConnection(JDBC_URL)) {
      try (Statement stmt = conn.createStatement()) {
        //stmt.executeUpdate("drop table if exists files");
        //stmt.executeUpdate("drop table files");
        stmt.executeUpdate("create table files (filename varchar(4096), version integer, last_modified timestamp, deleted boolean)");
        stmt.close();
      }
    }
  }

  public Map<String, FileDefinition> findAllFiles() throws SQLException {
    Map<String, FileDefinition> files = new HashMap<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      try (Statement stmt = conn.createStatement()) {
        try (ResultSet rs = stmt.executeQuery("select " + getColumns(FILES_COLUMNS) + " from " + FILES_TABLE + " f1 where f1.version=(select max(version) from " + FILES_TABLE + " f2 where f2.filename=f1.filename)")) {
          while (rs.next()) {
            FileDefinition fd = mapFileDefinition(rs);
            files.put(getFilename(fd.getFile()), fd);
          }
        }
      }
    }

    return files;
  }

  public Map<String, FileDefinition> findFilesByFilename(Collection<String> filenames) throws SQLException {
    Map<String, FileDefinition> files = new HashMap<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("select " + getColumns(FILES_COLUMNS) + " from " + FILES_TABLE + " f1 where f1.filename=? and f1.version=(select max(version) from " + FILES_TABLE + " f2 where f2.filename=f1.filename)")) {
        for (String filename : filenames) {
          stmt.setString(1, filename);
          try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
              FileDefinition fd = mapFileDefinition(rs);
              files.put(getFilename(fd.getFile()), fd);
            }
          }
        }
      }
    }
    return files;
  }

  public List<FileVersion> findOldDeletedFiles(LocalDateTime cutoff) throws SQLException {
    List<FileVersion> fileVersions = new ArrayList<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      //try (PreparedStatement stmt = conn.prepareStatement("select filename from " + FILES_TABLE + " f1 where version=(select max(version) from " + FILES_TABLE + " f2 where f2.filename=f1.filename) and deleted=1 and version<=?")) {
      String sql = "select filename, version from files f0 where filename in (select filename from files f1 where version=(select max(version) from files f2 where f2.filename=f1.filename) and deleted='t' and last_modified<=?)";
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setTimestamp(1, Timestamp.valueOf(cutoff));
        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            String filename = rs.getString("filename");
            int version = rs.getInt("version");

            FileVersion fileVersion = new FileVersion(filename, version);
            fileVersions.add(fileVersion);
          }
        }
      }
    }
    return fileVersions;
  }

  public List<FileVersion> findRetiredVersions(int retainVersions) throws SQLException {
    List<FileVersion> fileVersions = new ArrayList<>();
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("select filename, version from " + FILES_TABLE + " f1 where version<=(select max(version)-? from files f2 where f2.filename=f1.filename)")) {
        stmt.setInt(1, retainVersions);
        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            String filename = rs.getString("filename");
            int version = rs.getInt("version");

            FileVersion fileVersion = new FileVersion(filename, version);
            fileVersions.add(fileVersion);
          }
        }
      }
    }
    return fileVersions;
  }

  public int insertFiles(List<FileDefinition> files) throws SQLException {
    int inserts = 0;
    try (Connection conn = getConnection(JDBC_URL)) {
      String sql = "insert into " + FILES_TABLE + " (" + getColumns(FILES_COLUMNS) + ") values (?, ?, ?, ?)";
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        for (FileDefinition file : files) {
          stmt.setString(1, getFilename(file.getFile()));
          stmt.setInt(2, file.getVersion());
          stmt.setTimestamp(3, Timestamp.valueOf(file.getLastModified()));
          stmt.setBoolean(4, file.isDeleted());
          inserts += stmt.executeUpdate();
        }
      }
    }
    return inserts;
  }

  public int deleteFiles(List<FileVersion> fileVersions) throws SQLException {
    int deletes = 0;
    try (Connection conn = getConnection(JDBC_URL)) {
      try (PreparedStatement stmt = conn.prepareStatement("delete from " + FILES_TABLE + " where filename=? and version=?")) {
        for (FileVersion fileVersion : fileVersions) {
          stmt.setString(1, fileVersion.getFilename());
          stmt.setInt(2, fileVersion.getVersion());
          deletes += stmt.executeUpdate();
        }
      }
    }
    return deletes;
  }

  private FileDefinition mapFileDefinition(ResultSet rs) throws SQLException {
    String filename = rs.getString("filename");
    int version = rs.getInt("version");
    Timestamp lastModified = rs.getTimestamp("last_modified");
    boolean deleted = rs.getBoolean("deleted");

    FileDefinition fd = new FileDefinition();
    fd.setFile(getPath(filename));
    fd.setVersion(version);
    fd.setLastModified(lastModified.toLocalDateTime());
    fd.setDeleted(deleted);
    return fd;
  }

  private String getColumns(String[] cols) {
    return Arrays.stream(cols).collect(Collectors.joining(", "));
  }

  private Connection getConnection(String url) throws SQLException {
    /*
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL(url);
    ds.setUser("sa");
    ds.setPassword("sa");
    return ds.getConnection();
    */
    return DriverManager.getConnection(url);
  }
}
