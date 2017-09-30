package s4lab.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;
import s4lab.agent.FileUploadJob;
import s4lab.agent.FileUploadState;

import java.io.File;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.UUID;

public class FileRepository {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final DbHandler dbHandler;

  public FileRepository(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public void saveSimple(UUID directoryId, File file) {
    String id = UUID.randomUUID().toString();

    dbHandler.buildQuery("insert into file(id, filename, directory_id) values (?, ?, ?)")
            .withParam().stringValue(1, id)
            .withParam().stringValue(2, file.toString())
            .withParam().stringValue(3, directoryId.toString())
            .executeUpdate();

    dbHandler.buildQuery("insert into file_version (file_id, version, modified, deleted) values (?, ?, ?, ?)")
            .withParam().stringValue(1, id)
            .withParam().intValue(2, 0)
            .withParam().timestampValue(3, TimeUtils.at(file.lastModified()).toZonedDateTime())
            .withParam().booleanValue(4, false)
            .executeUpdate();
  }

  public tmpFileAndVersion getLatestVersionOByFilename(String filename) throws SQLException {
    return dbHandler.buildQuery("select f.id, f.filename, v.version, v.modified, v.deleted from file f join file_version v on f.id=v.file_id where f.filename=? and v.version=(select max(version) from file_version vm where vm.file_id=f.id)")
            .withParam().stringValue(1, filename)
            .executeQueryForObject(rs -> {
              tmpFileAndVersion fav = new tmpFileAndVersion();
              fav.setId(rs.getString(1));
              fav.setFilename(rs.getString(2));
              fav.setVersion(rs.getInt(3));
              fav.setModified(rs.getTimestamp(4));
              fav.setDeleted(rs.getBoolean(5));
              return fav;
            });
  }

  public void saveNewVersion(String fileId, int version, ZonedDateTime modified, boolean deleted) throws SQLException {
    dbHandler.buildQuery("insert into file_version (file_id, version, modified, deleted) values (?, ?, ?, ?)")
            .withParam().stringValue(1, fileId)
            .withParam().intValue(2, version)
            .withParam().timestampValue(3, modified)
            .withParam().booleanValue(4, deleted)
            .executeUpdate();
  }


  public void setUploadState(FileUploadJob job, FileUploadState state) {
    setUploadState(job.getFileId(), job.getVersion(), state.toString());
  }

  public void setUploadState(UUID fileId, int version, String uploadState) {
    dbHandler.buildQuery("update file_version set upload_state=? where file_id=? and version=?")
            .withParam().stringValue(1, uploadState)
            .withParam().uuidValue(2, fileId)
            .withParam().intValue(3, version)
            .executeUpdate();
  }


  public int insertFile(UUID fileId, File file, UUID directoryId) {
    return dbHandler.buildQuery("insert into file (id, filename, directory_id) values (?, ?, ?)")
            .withParam().uuidValue(1, fileId)
            .withParam().fileValue(2, file)
            .withParam().uuidValue(3, directoryId)
            .executeUpdate();
  }

  public int insertFileVersion(UUID fileId, int version, ZonedDateTime modified, boolean deleted) {
    return dbHandler.buildQuery("insert into file_version (file_id, version, modified, deleted) values (?, ?, ?, ?)")
            .withParam().uuidValue(1, fileId)
            .withParam().intValue(2, version)
            .withParam().timestampValue(3, modified)
            .withParam().booleanValue(4, deleted)
            .executeUpdate();
  }

  public void deleteConfiguredDirectory(UUID directoryId) {
    dbHandler.buildQuery("delete from file_version where file_id in (select f.id from file f join directory_config dc on dc.id=f.directory_id where dc.id=?)")
            .withParam().uuidValue(1, directoryId)
            .executeUpdate();

    dbHandler.buildQuery("delete from file where directory_id=?")
            .withParam().uuidValue(1, directoryId)
            .executeUpdate();

    dbHandler.buildQuery("delete from directory_config where id=?")
            .withParam().uuidValue(1, directoryId)
            .executeUpdate();
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
