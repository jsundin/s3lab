package ng3.common;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;

abstract public class ScheduledTask implements Runnable {
  private final Semaphore semaphore = new Semaphore(1);
  private final ErrorCallback errorCallback;
  private final Object lock = new Object();
  private volatile ScheduledFuture<?> future;
  private int executionCount;

  protected ScheduledTask(ErrorCallback errorCallback) {
    this.errorCallback = errorCallback;
  }

  @Override
  public void run() {
    semaphore.acquireUninterruptibly();
    executionCount++;

    try {
      if (performTask()) {
        scheduleTask();
      }
    } catch (Throwable t) {
      semaphore.release();
      if (errorCallback != null) {
        errorCallback.run(t);
      }
      return;
    }

    semaphore.release();
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

  public int getExecutionCount() {
    return executionCount;
  }

  abstract protected boolean performTask();
  abstract protected ScheduledFuture<?> scheduleFuture(boolean forceNow);
}
