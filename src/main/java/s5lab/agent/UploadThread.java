package s5lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.BackupJob;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * @author johdin
 * @since 2017-11-02
 */
public class UploadThread extends Thread {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BackupAgentContext ctx;
  private final BackupJob job;
  private volatile boolean finished = false;

  public UploadThread(BackupAgentContext ctx, BackupJob job) {
    super("Upload");
    this.ctx = ctx;
    this.job = job;
  }

  public void finish() {
    finished = true;
    try {
      join();
    } catch (InterruptedException ignored) {}
  }

  @Override
  public void run() {
    long processedFiles = 0;

    logger.info("UploadThread started for job '{}'", job.getConfiguration().getDirectory());
    while (!finished) {
      FileResult fileResult = ctx.dbClient.buildQuery("select id, job_id, filename from file where job_id=? and last_modified>last_upload_start or last_upload_start is null fetch next 1 rows only")
          .withParam().uuidValue(1, job.getId())
          .executeQueryForObject(rs -> {
            FileResult _rs = new FileResult();
            _rs.fileId = rs.getUuid(1);
            _rs.jobId = rs.getUuid(2);
            _rs.file = rs.getFile(3);
            return _rs;
          });

      if (fileResult == null) {
        try {
          Thread.sleep(500); // TODO: setting!
        } catch (InterruptedException ignored) {}
        continue;
      }

      processedFiles += ctx.dbClient.buildQuery("update file set last_upload_start=? where id=?")
          .withParam().timestampValue(1, ZonedDateTime.now())
          .withParam().uuidValue(2, fileResult.fileId)
          .executeUpdate();
      job.getConfiguration().getBackupProvider().enqueue(job.getId(), fileResult.file, job.getConfiguration());
    }

    logger.info("UploadThread finished for job '{}', processed {} files", job.getConfiguration().getDirectory(), processedFiles);
  }

  private class FileResult {
    UUID fileId;
    UUID jobId;
    File file;
  }
}
