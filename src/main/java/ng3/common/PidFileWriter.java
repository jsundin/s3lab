package ng3.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class PidFileWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File file;
  private final BlockingLatch blockingLatch;
  private volatile boolean finished = false;

  public PidFileWriter(File file, BlockingLatch blockingLatch) {
    this.file = file;
    this.blockingLatch = blockingLatch;
  }

  public boolean start() {
    String name = ManagementFactory.getRuntimeMXBean().getName();
    if (name == null) {
      logger.error("Could not get pid");
      return false;
    }
    int ati = name.indexOf('@');
    if (ati <= 0) {
      logger.error("Could not get pid");
      return false;
    }
    String pid = name.substring(0, ati);
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(pid.getBytes());
    } catch (IOException e) {
      logger.error("Could not write pid to '" + file + "'", e);
      return false;
    }
    logger.info("Wrote pid '{}' to '{}'", pid, file);

    new Thread(new Task(), "PidFile").start();
    return true;
  }

  public void finish() {
    finished = true;
    if (file.exists()) {
      file.delete();
    }
  }

  private class Task implements Runnable {
    @Override
    public void run() {
      logger.debug("Watching '{}'", file);
      while (!finished) {
        if (!file.exists()) {
          logger.debug("'{}' was removed", file);
          blockingLatch.release();
          break;
        }

        try {
          Thread.sleep(200);
        } catch (InterruptedException ignored) {}
      }
      logger.debug("Stopped watching '{}'", file);
    }
  }
}
