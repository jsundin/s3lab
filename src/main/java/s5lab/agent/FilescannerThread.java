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
    logger.info("Watching filescannerQueue");
    long itemsProcessed = 0;
    long itemsInsertedOrUpdated = 0;

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
      ExistingFile existingFile = dbClient.buildQuery("select id, last_modified, last_upload_start, last_upload_finished from file where job_id=? and filename=?")
              .withParam().uuidValue(1, event.getJobId())
              .withParam().fileValue(2, event.getFile())
              .executeQueryForObject(rs -> {
                ExistingFile ref = new ExistingFile();
                ref.id = rs.getUuid(1);
                ref.lastModified = rs.getTimestamp(2);
                ref.lastUploadStart = rs.getTimestamp(3);
                ref.lastUploadFinished = rs.getTimestamp(4);
                return ref;
              });

      ZonedDateTime lastModified = FileTools.lastModified(event.getFile());
      if (existingFile == null) {
        itemsInsertedOrUpdated += dbClient.buildQuery("insert into file (id, job_id, filename, last_modified) values (?, ?, ?, ?)")
                .withParam().uuidValue(1, UUID.randomUUID())
                .withParam().uuidValue(2, event.getJobId())
                .withParam().fileValue(3, event.getFile())
                .withParam().timestampValue(4, lastModified)
                .executeUpdate();
      } else {
        if (existingFile.lastModified.isBefore(lastModified)) {
          itemsInsertedOrUpdated += dbClient.buildQuery("update file set last_modified=? where id=?")
              .withParam().timestampValue(1, lastModified)
              .withParam().uuidValue(2, existingFile.id)
              .executeUpdate();
        }
      }
    }
    logger.info("Stopped watching filescannerQueue with {} items left ({} processed, {} inserted/updated)", filescannerQueue.size(), itemsProcessed, itemsInsertedOrUpdated);
  }

  private class ExistingFile {
    UUID id;
    ZonedDateTime lastModified;
    ZonedDateTime lastUploadStart;
    ZonedDateTime lastUploadFinished;
  }
}
