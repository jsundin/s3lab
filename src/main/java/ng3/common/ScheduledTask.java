package ng3.common;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;

abstract public class ScheduledTask implements Runnable {
  private final Semaphore semaphore = new Semaphore(1);
  private final Object lock = new Object();
  private volatile ScheduledFuture<?> future;

  @Override
  public void run() {
    semaphore.acquireUninterruptibly();

    try {
      if (performTask()) {
        scheduleTask();
      }
    } finally {
      semaphore.release();
    }
  }

  public void scheduleTask() {
    scheduleTask(false);
  }

  public void scheduleTask(boolean forceNow) {
    synchronized (lock) {
      if (future == null || !future.isCancelled()) {
        future = scheduleFuture(forceNow);
      }
    }
  }

  public void cancel() {
    synchronized (lock) {
      if (future != null && !future.isCancelled()) {
        future.cancel(false);
      }
    }

    semaphore.acquireUninterruptibly();
  }

  abstract protected boolean performTask();
  abstract protected ScheduledFuture<?> scheduleFuture(boolean forceNow);
}
