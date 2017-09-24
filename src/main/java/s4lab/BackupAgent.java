package s4lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s3lab.Looper;
import s4lab.db.FileRepository;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static s3lab.Utils.longToLDT;

public class BackupAgent {
  private static final Logger logger = LoggerFactory.getLogger(BackupAgent.class);
  private BackupAgentThread baThread;
  private FileScanResultThread fileScanResultThread;
  private final FileRepository fileRepository;

  public BackupAgent(FileRepository fileRepository) {
    this.fileRepository = fileRepository;
  }

  public void start() {
    if (baThread != null) {
      throw new IllegalStateException("BackupAgent already running");
    }
    baThread = new BackupAgentThread();
    baThread.start();

    fileScanResultThread = new FileScanResultThread();
    fileScanResultThread.start();
    logger.info("BackupAgent started");
  }

  public void finish(boolean graceful) {
    if (baThread == null) {
      throw new IllegalStateException("BackupAgent not running");
    }
    baThread.looper.finish();
    fileScanResultThread.finish(graceful);
    logger.info("BackupAgent finished");
  }

  public Queue<File> getFileScanQueue() {
    return fileScanResultThread.fileScanQueue;
  }

  private class FileScanResultThread extends Thread {
    private final BlockingQueue<File> fileScanQueue = new LinkedBlockingQueue<>();
    private volatile boolean finished = false;

    FileScanResultThread() {
      super("FileScanResult");
    }

    void finish(boolean graceful) {
      if (!graceful || fileScanQueue.isEmpty()) {
        finished = true;
        interrupt();
      }
    }

    @Override
    public void run() {
      do {
        try {
          File file = fileScanQueue.take();
          try {
            FileRepository.tmpFileAndVersion saved = fileRepository.getLatestVersionOByFilename(file.toString());
            if (saved != null) {
              LocalDateTime lastModified = longToLDT(file.lastModified());
              if (saved.isDeleted() || !saved.getModified().equals(lastModified)) {
                System.out.println("UPDATE: " + file);
                fileRepository.saveNewVersion(saved.getId(), saved.getVersion() + 1, lastModified, false);
              }
            } else {
              // create
              System.out.println("CREATE: " + file);
              fileRepository.saveSimple(file);
            }
          } catch (SQLException e) {
            e.printStackTrace();
          }
        } catch (InterruptedException ignored) {}
      } while (!finished);
    }
  }

  private class BackupAgentThread extends Thread {
    final Looper looper = new Looper();

    BackupAgentThread() {
      super("BackupAgent");
    }

    @Override
    public void run() {
      looper.loop();
    }
  }
}
