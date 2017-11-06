package ng3.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class PidfileWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File file;
  private final LatchSynchronizer latchSynchronizer;
  private volatile boolean finished = false;

  public PidfileWriter(File file, LatchSynchronizer latchSynchronizer) {
    this.file = file;
    this.latchSynchronizer = latchSynchronizer;
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

    new SimpleThreadFactory("PidFile").newThread(task).start();
    return true;
  }

  public void finish() {
    finished = true;
    if (file.exists()) {
      file.delete();
    }
  }

  private Runnable task = new Runnable() {
    @Override
    public void run() {
      logger.debug("Watching '{}'", file);
      while (!finished) {
        if (!file.exists()) {
          logger.debug("'{}' was removed", file);
          latchSynchronizer.releaseAllSemaphores();
          break;
        }

        try {
          Thread.sleep(200);
        } catch (InterruptedException ignored) {}
      }
      logger.debug("Stopped watching '{}'", file);
    }
  };
}
