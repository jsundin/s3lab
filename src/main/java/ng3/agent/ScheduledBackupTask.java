package ng3.agent;

import ng3.BackupState;
import ng3.common.BlockingLatch;
import ng3.common.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class ScheduledBackupTask extends ScheduledTask {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BackupAgent.BackupAgentContext ctx;
  private final boolean reschedule;
  private final BlockingLatch blockingLatch;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(r, "BackupTask"));
  private final ScheduledBackupJob callback;
  private int executionCount = 0;

  ScheduledBackupTask(BackupAgent.BackupAgentContext ctx, boolean reschedule, BlockingLatch blockingLatch, ScheduledBackupJob callback) {
    this.ctx = ctx;
    this.reschedule = reschedule;
    this.blockingLatch = blockingLatch;
    this.callback = callback;
  }

  void shutdown() {
    cancel();
    scheduler.shutdown();
  }

  @Override
  protected boolean performTask() {
    executionCount++;
    BackupState state = ctx.dbClient.getBackupState();
    state.setLastStarted(ZonedDateTime.now());
    ctx.dbClient.saveBackupState(state);

    try {
      callback.runBackupJob(ctx);
    } catch (Throwable t) {
      logger.error("Caught unexpected error - trying to quit", t);
      blockingLatch.release();
      return false;
    }

    if (!reschedule) {
      notifyFinish();
    }
    return reschedule;
  }

  @Override
  protected ScheduledFuture<?> scheduleFuture(boolean forceNow) {
    ZonedDateTime next = null;
    if (!forceNow) {
      BackupState state = ctx.dbClient.getBackupState();
      if (state != null && state.getLastStarted() != null) {
        next = state.getLastStarted().plusMinutes(ctx.configuration.getIntervalInMinutes());
        if (next.isBefore(ZonedDateTime.now())) {
          next = null;
        }
      }
    }

    logger.info("Next backup scheduled " + (next == null ? "NOW" : "at " + next));
    if (next == null) {
      return scheduler.schedule(this, 1, TimeUnit.MILLISECONDS);
    } else {
      long millis = ChronoUnit.MILLIS.between(ZonedDateTime.now(), next);
      return scheduler.schedule(this, millis, TimeUnit.MILLISECONDS);
    }
  }

  int getExecutionCount() {
    return executionCount;
  }

  private void notifyFinish() {
    blockingLatch.release();
  }

  interface ScheduledBackupJob {
    void runBackupJob(BackupAgent.BackupAgentContext ctx);
  }
}
