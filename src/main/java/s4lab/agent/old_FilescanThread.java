package s4lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;
import s4lab.db.DbHandler;

import java.io.File;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class old_FilescanThread {
  private static int threadId = 1;
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final Worker worker = new Worker();
  private final UUID directoryId;
  private final DbHandler dbHandler;

  public old_FilescanThread(DbHandler dbHandler, UUID directoryId) {
    this.directoryId = directoryId;
    this.dbHandler = dbHandler;
  }

  public FilescanHandle start() {
    worker.start(Thread.currentThread());
    synchronized (worker) {
      try {
        worker.wait();
      } catch (InterruptedException ignored) {}
    }

    return new FilescanHandle() {
      @Override
      public void finish() {
        worker.filescanEventQueue.add(FilescanEvent.finishedEvent());
      }

      @Override
      public void fileFound(File file) {
        synchronized (worker) {
          if (!worker.running) {
            throw new IllegalStateException("Invalid state for operation");
          }
          worker.filescanEventQueue.add(FilescanEvent.fileFoundEvent(file));
        }
      }
    };
  }

  private class Worker extends Thread {
    private final BlockingQueue<FilescanEvent> filescanEventQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;

    Worker() {
      super("Filescan-" + (threadId++));
    }

    public void start(Thread parentThread) {
      start();
    }

    @Override
    public void run() {
      logger.info("Started");
      synchronized (this) {
        running = true;
        notify();
      }
      try {
        queueWorker();
      } catch (Throwable t) {
        logger.error("Finished due to error", t);
      }
      synchronized (worker) {
        running = false;
      }
      logger.info("Finished");
    }

    void queueWorker() {
      boolean finished = false;
      running = true;
      do {
        FilescanEvent event;
        try {
          event = filescanEventQueue.take();
        } catch (InterruptedException e) {
          continue;
        }

        switch (event.type) {
          case FINISHED:
            finished = true;
            break;

          case FILE_FOUND:
            logger.info("File found: {} - {}", event.file, directoryId);
            break;

          default:
            throw new IllegalStateException("Unknown event type: " + event.type);
        }
      } while (!finished);
    }

    void fileFound(File file) {
      ZonedDateTime lastModified;
      if (file.exists()) {
        lastModified = TimeUtils.at(file.lastModified()).toZonedDateTime();
      } else {
        lastModified = ZonedDateTime.now();
      }

      synchronized (dbHandler) {
        /*
        try (Connection c = dbHandler.getConnection()) {

        }
        */
      }
    }
  }

  public interface FilescanHandle {
    void finish();
    void fileFound(File file);
  }

  private static class FilescanEvent {
    FilescanEventType type;
    File file;

    private FilescanEvent(FilescanEventType type) {
      this.type = type;

    }

    static FilescanEvent finishedEvent() {
      return new FilescanEvent(FilescanEventType.FINISHED);
    }

    static FilescanEvent fileFoundEvent(File file) {
      FilescanEvent fe = new FilescanEvent(FilescanEventType.FILE_FOUND);
      fe.file = file;
      return fe;
    }
  }

  private enum FilescanEventType {
    FINISHED,
    FILE_FOUND
  }
}
