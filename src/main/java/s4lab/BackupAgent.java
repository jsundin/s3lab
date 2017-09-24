package s4lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s3lab.Looper;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BackupAgent {
  private static final Logger logger = LoggerFactory.getLogger(BackupAgent.class);
  private BackupAgentThread baThread;
  private FileScanResultThread fileScanResultThread;

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
          // här ska vi alltså kolla i databasen om filen finns redan,
          // och lagra ner den om den är förändrad på något vis
          System.out.println(file);
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
