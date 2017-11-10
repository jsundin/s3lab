package schedlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class Run {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private long t0;

  public static void main(String[] args) {
    new Run().run();
  }

  public void run() {
    t0 = System.currentTimeMillis();
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ScheduledExecutorService scheduler2 = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(new X("t1"), 0, 1000, TimeUnit.MILLISECONDS);
    scheduler2.scheduleAtFixedRate(new X("t2"), 100, 1000, TimeUnit.MILLISECONDS);

    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    shutdown(scheduler);
    shutdown(scheduler2);
  }

  private void shutdown(ScheduledExecutorService scheduler) {
    scheduler.shutdown();
    try {
      scheduler.awaitTermination(999, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private final Semaphore semaphore = new Semaphore(1);

  class X implements Runnable {
    private final String name;
    private long lastrun;

    X(String name) {
      this.name = name;
    }

    @Override
    public void run() {
      semaphore.acquireUninterruptibly();
      long t = 0;
      if (lastrun > 0) {
        t = System.currentTimeMillis() - lastrun;
      }
      lastrun = System.currentTimeMillis();
      logger.info("Running '{}', t={}, exec={}", name, t, System.currentTimeMillis() - t0);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      semaphore.release();
    }
  }
}
