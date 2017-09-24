package s4lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s3lab.Looper;

public class BackupAgent {
  private static final Logger logger = LoggerFactory.getLogger(BackupAgent.class);
  private BackupAgentThread baThread;

  public void start() {
    if (baThread != null) {
      throw new IllegalStateException("BackupAgent already running");
    }
    baThread = new BackupAgentThread();
    baThread.start();
    logger.info("BackupAgent started");
  }

  public void finish() {
    if (baThread == null) {
      throw new IllegalStateException("BackupAgent not running");
    }
    baThread.looper.finish();
    logger.info("BackupAgent finished");
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
