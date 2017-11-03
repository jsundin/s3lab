package ng3.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class PidfileWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final List<BlockingLatch> stakeholders = new ArrayList<>();
  private final File file;
  private volatile boolean finished = false;

  public PidfileWriter(File file) {
    this.file = file;
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

  public void addStakeholder(BlockingLatch latch) {
    synchronized (stakeholders) {
      stakeholders.add(latch);
    }
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
          synchronized (stakeholders) {
            for (BlockingLatch stakeholder : stakeholders) {
              stakeholder.release();
            }
          }
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
