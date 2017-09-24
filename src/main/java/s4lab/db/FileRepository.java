package s4lab.db;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

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
}
