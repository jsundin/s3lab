package ng3.drivers;

import ng3.db.DbClient;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

abstract class AbstractBackupDriver {
  private final DbClient dbClient;

  protected AbstractBackupDriver(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  protected List<BackupFile> getNextFiles(int n, List<UUID> directoryIds) {
    List<BackupFile> backupFiles = dbClient.buildQuery(
            "select file_id, filename, deleted, directory_id from file " +
                    "where directory_id in (" + questionMarks(directoryIds) + ") " +
                    "and upload_started is null or (upload_finished is not null and upload_started>upload_finished) or (upload_started is not null and last_modified>upload_started) " +
                    "fetch next 1 rows only")
            .withParam().uuidValues(1, directoryIds)
            .executeQuery(rs -> new BackupFile(rs.getUuid(1), rs.getFile(2), rs.getBoolean(3), rs.getUuid(4)));

    if (!backupFiles.isEmpty()) {
      dbClient.buildQuery("update file set upload_started=?, upload_finished=null where file_id in (" + questionMarks(backupFiles) + ")")
              .withParam().timestampValue(1, ZonedDateTime.now())
              .withParam().uuidValues(2, backupFiles.stream().map(v -> v.id).collect(Collectors.toList()))
              .executeUpdate();
    }
    return backupFiles;
  }

  private String questionMarks(List<?> objs) {
    return IntStream.range(0, objs.size()).mapToObj(v -> "?").collect(Collectors.joining(","));
  }

  protected class BackupFile {
    protected final UUID id;
    protected final File file;
    protected final boolean deleted;
    protected final UUID directoryId;

    private BackupFile(UUID id, File file, boolean deleted, UUID directoryId) {
      this.id = id;
      this.file = file;
      this.deleted = deleted;
      this.directoryId = directoryId;
    }
  }
}
