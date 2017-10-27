package s5lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.FileTools;
import s5lab.Settings;
import s5lab.db.DbClient;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class FilescannerThread extends Thread {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  final BlockingQueue<FilescannerEvent> filescannerQueue = new LinkedBlockingQueue<>();
  private final DbClient dbClient;
  private volatile boolean finished = false;
  private volatile boolean graceful = false;

  FilescannerThread(DbClient dbClient) {
    super("FileScanner");
    this.dbClient = dbClient;
  }

  public void finish(boolean graceful) {
    finished = true;
    this.graceful = graceful;
    try {
      join();
    } catch (InterruptedException ignored) {}
  }

  @Override
  public void run() {
    logger.info("watching filescannerQueue");
    int itemsProcessed = 0;

    while (!finished || (finished && graceful && !filescannerQueue.isEmpty())) {
      FilescannerEvent event;
      try {
        event = filescannerQueue.poll(Settings.FILESCANNER_POLL_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
        if (event == null) {
          continue;
        }
      } catch (InterruptedException e) {
        continue;
      }

      itemsProcessed++;
      FileXYZ xyz = dbClient.buildQuery("select id, last_modified, last_upload_start, last_upload_finished from file where job_id=? and filename=?")
              .withParam().uuidValue(1, event.getJobId())
              .withParam().fileValue(2, event.getFile())
              .executeQueryForObject(rs -> {
                FileXYZ fxyz = new FileXYZ();
                fxyz.id = rs.getUuid(1);
                fxyz.lastModified = rs.getTimestamp(2);
                fxyz.lastUploadStart = rs.getTimestamp(3);
                fxyz.lastUploadFinished = rs.getTimestamp(4);
                return fxyz;
              });

      ZonedDateTime lastModified = FileTools.lastModified(event.getFile());
      System.out.println(event.getFile() + ": " + xyz);
      if (xyz == null) {
        dbClient.buildQuery("insert into file (id, job_id, filename, last_modified) values (?, ?, ?, ?)")
                .withParam().uuidValue(1, UUID.randomUUID())
                .withParam().uuidValue(2, event.getJobId())
                .withParam().fileValue(3, event.getFile())
                .withParam().timestampValue(4, lastModified)
                .executeUpdate();
      } else {
        dbClient.buildQuery("update file set last_modified=? where id=?")
                .withParam().timestampValue(1, lastModified)
                .withParam().uuidValue(2, xyz.id)
                .executeUpdate();
      }
    }
    logger.info("Stopped watching filescannerQueue with {} items left ({} processed)", filescannerQueue.size(), itemsProcessed);
  }

  private class FileXYZ {
    UUID id;
    ZonedDateTime lastModified;
    ZonedDateTime lastUploadStart;
    ZonedDateTime lastUploadFinished;
  }
}
