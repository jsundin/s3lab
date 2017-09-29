package s4lab.agent;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.db.DbHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileUploadManager {
  private final int uploadThreads;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final DbHandler dbHandler;
  private UploadJobProducer uploadJobProducer;
  private List<UploadJobConsumer> uploadJobConsumers = new ArrayList<>();
  private final BlockingQueue<FileUploadJob> jobQueue = new LinkedBlockingQueue<>();
  private static int uploadJobConsumerId = 0;

  public FileUploadManager(DbHandler dbHandler, int uploadThreads) {
    this.dbHandler = dbHandler;
    this.uploadThreads = uploadThreads;
  }

  public void start() {
    uploadJobProducer = new UploadJobProducer();
    uploadJobProducer.start();

    for (int i = 0; i < uploadThreads; i++) {
      UploadJobConsumer consumer = new UploadJobConsumer();
      consumer.start();
      uploadJobConsumers.add(consumer);
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
                  FileUploadJob fuj = new FileUploadJob();
                  fuj.fileId = rs.getUuid(1);
                  fuj.version = rs.getInt(2);
                  fuj.file = rs.getFile(3);
                  fuj.deleted = rs.getBoolean(4);
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

        dbHandler.buildQuery("update file_version set upload_state='STARTED' where file_id=? and version=?")
                .withParam().uuidValue(1, job.fileId)
                .withParam().intValue(2, job.version)
                .executeUpdate();
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

          /*dbHandler.buildQuery("update file_version set upload_state='UPLOADING' where file_id=? and version=?")
                  .withParam().uuidValue(1, job.fileId)
                  .withParam().intValue(2, job.version)
                  .executeUpdate();*/
          uploadFile(job.file);
          //System.out.println("UPLOAD: " + job.file + (job.deleted ? " [DELETED]" : ""));
          //Thread.sleep(200);
        } catch (InterruptedException ignored) {}
      } while (!allowedToFinish);
    }
  }

  private void uploadFile(File file) {
    File targetFile = new File("/tmp/backuptarget", file.toString());
    File targetDir = targetFile.getParentFile();
    if (!targetDir.exists()) {
      targetDir.mkdirs();
    }
    try {
      System.out.println("BACKUP: " + file);
      FileUtils.copyFile(file, targetFile, true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private class FileUploadJob {
    private UUID fileId;
    private int version;
    private File file;
    private boolean deleted;
  }
}
