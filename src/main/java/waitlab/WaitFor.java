package waitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class WaitFor {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public static void main(String[] args) throws Exception {
    new WaitFor().run();
  }

  public void run() throws Exception {
    TestTask task = new TestTask();
    task.reschedule();

    Thread.sleep(2000);
    task.cancel();

    Thread.sleep(10000);
    scheduler.shutdown();
  }

  abstract class ScheduledTask implements Runnable {
    private final Semaphore semaphore = new Semaphore(1);
    private final Object lock = new Object();
    private volatile ScheduledFuture<?> future;

    @Override
    public void run() {
      logger.info("run -- started");
      semaphore.acquireUninterruptibly();

      try {
        internalRun();
        reschedule();
      } finally {
        semaphore.release();
      }
      logger.info("run -- finished");
    }

    public void cancel() {
      logger.info("cancel -- started");
      synchronized (lock) {
        if (future != null) {
          future.cancel(false);
        }
      }
      logger.info("cancel -- futures cancelled");
      semaphore.acquireUninterruptibly();
      logger.info("cancel -- complete");
    }

    public void reschedule() {
      synchronized (lock) {
        if (future == null || !future.isCancelled()) {
          logger.info("reschedule - one more go");
          future = rescheduleInternal();
        } else {
          logger.info("reschedule - no more");
        }
      }
    }

    abstract void internalRun();

    abstract ScheduledFuture<?> rescheduleInternal();
  }

  class TestTask extends ScheduledTask {

    @Override
    void internalRun() {
      try {
        Thread.sleep(2);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    @Override
    ScheduledFuture<?> rescheduleInternal() {
      return scheduler.schedule(this, 500, TimeUnit.MILLISECONDS);
    }
  }
}
