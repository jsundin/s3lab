package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ng3.agent.BackupReportWriter;
import ng3.common.SimpleThreadFactory;
import ng3.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class FileCopyBackupDriver implements BackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File path;
  private final boolean createPathIfNeeded;

  @JsonCreator
  public FileCopyBackupDriver(
          @JsonProperty("path") File path,
          @JsonProperty("create-if-needed") boolean createPathIfNeeded) {
    this.path = path;
    this.createPathIfNeeded = createPathIfNeeded;
  }

  @Override
  public BackupSession startSession(DbClient dbClient, BackupReportWriter report, UUID planId) {
    dbClient.buildQuery("update file set upload_started=null").executeUpdate(); // TODO: bort :)

    BackupTask task = new BackupTask(dbClient, planId, report);
    new SimpleThreadFactory("FileCopy").newThread(task).start();
    return new FileCopyBackupSession(task);
  }

  public class FileCopyBackupSession implements BackupSession {
    private final BackupTask task;

    public FileCopyBackupSession(BackupTask task) {
      this.task = task;
    }

    @Override
    public void finish() {
      task.sessionFinished.release();
      task.finished.acquireUninterruptibly();
    }
  }

  private class BackupTask implements Runnable {
    private final DbClient dbClient;
    private final UUID planId;
    private final Semaphore finished = new Semaphore(0);
    private final Semaphore sessionFinished = new Semaphore(0);
    private final BackupReportWriter report;

    private BackupTask(DbClient dbClient, UUID planId, BackupReportWriter report) {
      this.dbClient = dbClient;
      this.planId = planId;
      this.report = report;
    }

    @Override
    public void run() {
      try {
        report.getTargetReportWriter().setStartedAt(ZonedDateTime.now());
        loop();
        report.getTargetReportWriter().setFinishedAt(ZonedDateTime.now());
      } catch (Throwable error) {
        logger.error("Unhandled error for plan '{}'", planId);
        logger.error("", error);
        report.addError("Unhandled error in backup driver, see system logs for details");
      } finally {
        finished.release();
      }
    }

    private void loop() {
      while (true) {
        XYZ xyz = dbClient.buildQuery("select file_id, filename, deleted from file f join directory d on d.directory_id=f.directory_id where d.plan_id=? and upload_started is null or (upload_finished is not null and upload_started>upload_finished) or (upload_started is not null and last_modified>upload_started) fetch next 1 rows only")
                .withParam().uuidValue(1, planId)
                .executeQueryForObject(rs -> new XYZ(rs.getUuid(1), rs.getFile(2), rs.getBoolean(3)));
        if (xyz == null) {
          if (sessionFinished.availablePermits() >= 0) {
            break;
          }
          try {
            Thread.sleep(200); // TODO: Settings
          } catch (InterruptedException e) {
            Thread.interrupted();
          }
          continue;
        }
        report.getTargetReportWriter().processedFile();
        dbClient.buildQuery("update file set upload_started=? where file_id=?")
                .withParam().timestampValue(1, ZonedDateTime.now())
                .withParam().uuidValue(2, xyz.id)
                .executeUpdate();
      }
    }
  }

  private class XYZ {
    private final UUID id;
    private final File file;
    private final boolean deleted;

    private XYZ(UUID id, File file, boolean deleted) {
      this.id = id;
      this.file = file;
      this.deleted = deleted;
    }
  }
}
