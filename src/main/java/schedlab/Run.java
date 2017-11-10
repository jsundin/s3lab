package schedlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class Run {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public static void main(String[] args) {
    new Run().run();
  }

  public void run() {
    long t0 = System.currentTimeMillis();
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    //scheduler.scheduleWithFixedDelay(new X(t0), 1000, 1000, TimeUnit.MILLISECONDS);
    scheduler.scheduleAtFixedRate(new X(), -1000, 1000, TimeUnit.MILLISECONDS);
    scheduler.scheduleAtFixedRate(new Y(), 500, 500, TimeUnit.MILLISECONDS);

    try {
      Thread.sleep(2300);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    logger.info("shutdown");
    scheduler.shutdown();
    logger.info("shutdown -- complete");
    try {
      scheduler.awaitTermination(999, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    logger.info("await complete");
  }

  volatile int running = 0;

  class X implements Runnable {
    @Override
    public void run() {
      running++;
      logger.info("x running [running={}]", running);
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      logger.info("x stopped running");
      running--;
    }
  }

  class Y implements Runnable {

    @Override
    public void run() {
      running++;
      logger.info("y running [running={}]", running);
      running--;
    }
  }
}
