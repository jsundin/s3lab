package s4lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.db.DbHandler;
import s4lab.target.BackupTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileUploadManager {
  private final int uploadThreads;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final DbHandler dbHandler;
  private final BackupTarget backupTarget;
  private UploadJobProducer uploadJobProducer;
  private List<UploadJobConsumer> uploadJobConsumers = new ArrayList<>();
  private final BlockingQueue<FileUploadJob> jobQueue = new LinkedBlockingQueue<>();
  private static int uploadJobConsumerId = 0;

  public FileUploadManager(DbHandler dbHandler, int uploadThreads, BackupTarget backupTarget) {
    this.dbHandler = dbHandler;
    this.uploadThreads = uploadThreads;
    this.backupTarget = backupTarget;
  }

  public void start() {
    // TODO: vi KAN ha ett race-condition här:
    // ponera att vi har en fil som misslyckades i förra körningen, koden nedan gör att den får upload_state=null
    // om den filen sedan ändrats mellan förra körningen och nu, så kan den förra köas innan den nya hittas
    // .. sedan om den nya körs parallellt så har vi problem
    int requeuedFiles = dbHandler.buildQuery("update file_version set upload_state=null where upload_state<>?")
            .withParam().stringValue(1, FileUploadState.FINISHED.toString())
            .executeUpdate();

    uploadJobProducer = new UploadJobProducer();
    uploadJobProducer.start();

    for (int i = 0; i < uploadThreads; i++) {
      UploadJobConsumer consumer = new UploadJobConsumer();
      consumer.start();
      uploadJobConsumers.add(consumer);
    }

    if (requeuedFiles > 0) {
      logger.info("Upload manager started, requeued {} files that did not upload properly", requeuedFiles);
    } else {
      logger.info("Upload manager started");
    }
  }

  public void finish() {
    try {
      uploadJobProducer.allowedToFinish = true;
      uploadJobProducer.join();

      logger.info("Waiting for upload queue to clear...");
      while (!jobQueue.isEmpty()) {
        Thread.sleep(100);
      }
      uploadJobConsumers.forEach(c -> c.allowedToFinish = true);
      for (UploadJobConsumer c : uploadJobConsumers) {
        c.join();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private class UploadJobProducer extends Thread {
    private final int QUEUE_WAIT_TIMEOUT = 500;
    private final int WAIT_FOR_JOB_TIMEOUT = 500;
    private volatile boolean allowedToFinish = false;

    public UploadJobProducer() {
      super("UploadJobProducer");
    }

    @Override
    public void run() {
      long t0 = System.currentTimeMillis();
      int processedJobs = 0;

      logger.info("Started");
      do {
        // TODO: vi vill bara ha senaste versionen av en fil
        FileUploadJob job = dbHandler.buildQuery("select v.file_id, v.version, f.filename, v.deleted from file_version v join file f on f.id=v.file_id where upload_state is null fetch next 1 rows only")
                .executeQueryForObject(rs -> {
                  FileUploadJob fuj = new FileUploadJob(
                          rs.getUuid(1),
                          rs.getInt(2),
                          rs.getFile(3),
                          rs.getBoolean(4)
                  );
                  return fuj;
                });
        if (allowedToFinish && job == null) {
          break;
        }

        if (job == null) {
          try {
            Thread.sleep(WAIT_FOR_JOB_TIMEOUT);
          } catch (InterruptedException ignored) {}
          continue;
        }

        while (jobQueue.size() >= uploadThreads) {
          try {
            Thread.interrupted();
            Thread.sleep(QUEUE_WAIT_TIMEOUT);
          } catch (InterruptedException ignored) {}
        }

        dbHandler.getFileRepository().setUploadState(job, FileUploadState.QUEUED);
        jobQueue.add(job);
        processedJobs++;
      } while (true);
      logger.info("Upload manager finished, processed {} files in {}ms", processedJobs, (System.currentTimeMillis() - t0));
    }
  }

  private class UploadJobConsumer extends Thread {
    private final int QUEUE_POLL_TIMEOUT = 500;
    private volatile boolean allowedToFinish;

    public UploadJobConsumer() {
      super("UploadJobConsumer-" + (uploadJobConsumerId++));
    }

    @Override
    public void run() {
      do {
        try {
          Thread.interrupted();
          FileUploadJob job = jobQueue.poll(QUEUE_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
          if (job == null) {
            continue;
          }

          backupTarget.handleJob(job);
        } catch (InterruptedException ignored) {}
      } while (!allowedToFinish);
    }
  }
}
