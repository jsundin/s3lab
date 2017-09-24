package s4lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s3lab.Looper;
import s4lab.db.FileRepository;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
  }

  public void finish() {
    if (baThread == null) {
      throw new IllegalStateException("BackupAgent not running");
    }
    baThread.looper.finish();
    fileScanResultThread.fileScanQueue.add(new FileEvent(FileEventType.SHUTDOWN));
  }

  public void fileScanStarted() {
    fileScanResultThread.fileScanQueue.add(new FileEvent(FileEventType.SCANNING_STARTED));
  }

  public void fileScanEnded() {
    fileScanResultThread.fileScanQueue.add(new FileEvent(FileEventType.SCANNING_ENDED));
  }

  public void fileScanned(File file) {
    fileScanResultThread.fileScanQueue.add(new FileEvent(FileEventType.FILE_FOUND, file));
  }

  private enum FileEventType {
    SCANNING_STARTED,
    FILE_FOUND,
    SCANNING_ENDED,
    SHUTDOWN
  }

  private static class FileEvent {
    private final FileEventType fileEventType;
    private final File file;

    public FileEvent(FileEventType fileEventType) {
      this.fileEventType = fileEventType;
      this.file = null;
    }

    public FileEvent(FileEventType fileEventType, File file) {
      this.fileEventType = fileEventType;
      this.file = file;
    }

    @Override
    public String toString() {
      return "FileEvent{" +
              "fileEventType=" + fileEventType +
              ", file=" + file +
              '}';
    }
  }

  private class FileScanResultThread extends Thread {
    private final BlockingQueue<FileEvent> fileScanQueue = new LinkedBlockingQueue<>();
    private volatile boolean finished = false;

    FileScanResultThread() {
      super("FileScanResult");
    }

    @Override
    public void run() {
      logger.info("FileScanResultThread started");
      boolean finished = false;
      boolean scanning = false;
      do {
        FileEvent fileEvent;
        try {
          fileEvent = fileScanQueue.take();
        } catch (InterruptedException ignored) {
          continue;
        }

        switch (fileEvent.fileEventType) {
          case SCANNING_STARTED:
            if (scanning) {
              throw new IllegalStateException("SCANNING_STARTED when scanning=true");
            }
            scanning = true;
            logger.info("Filescan started");
            break;

          case SCANNING_ENDED:
            if (!scanning) {
              throw new IllegalStateException("SCANNING_ENDED when scanning=false");
            }
            scanning = false;
            logger.info("Filescan ended, queueSize={}", fileScanQueue.size());
            break;

          case FILE_FOUND:
            if (!scanning) {
              throw new IllegalStateException("FILE_FOUND when scanning=false");
            }
            scanFile(fileEvent.file);
            break;

          case SHUTDOWN:
            logger.info("Received SHUTDOWN, scanning={}", scanning);
            finished = true;
            break;
        }
      } while (!finished);
      logger.info("FileScanResultThread finished, scanning={}, queueSize={}", scanning, fileScanQueue.size());
    }

    private void scanFile(File file) {
      LocalDateTime lastModified = longToLDT(file.lastModified());

      try {
        FileRepository.tmpFileAndVersion savedVersion = fileRepository.getLatestVersionOByFilename(file.toString());
        if (savedVersion == null) {
          System.out.println("CREATE: " + file);
          fileRepository.saveSimple(file);
        } else if (savedVersion.isDeleted() || !savedVersion.getModified().equals(lastModified)) {
          System.out.println("UPDATE: " + file);
          fileRepository.saveNewVersion(savedVersion.getId(), savedVersion.getVersion() + 1, lastModified, false);
        }
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
    }
  }

  private class BackupAgentThread extends Thread {
    final Looper looper = new Looper();

    BackupAgentThread() {
      super("BackupAgent");
    }

    @Override
    public void run() {
      logger.info("BackupAgent started");
      looper.loop();
      logger.info("BackupAgent finished");
    }
  }
}