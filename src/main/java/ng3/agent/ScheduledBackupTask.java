package ng3.agent;

import ng3.BackupState;
import ng3.common.BlockingLatch;
import ng3.common.ScheduledTask;
import ng3.conf.Configuration;
import ng3.db.DbClient;
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
  private final DbClient dbClient;
  private final Configuration configuration;
  private final boolean reschedule;
  private final BlockingLatch blockingLatch;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(r, "BackupTask"));
  private final Runnable callback;

  ScheduledBackupTask(DbClient dbClient, Configuration configuration, boolean reschedule, BlockingLatch blockingLatch, Runnable callback) {
    this.dbClient = dbClient;
    this.configuration = configuration;
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
    BackupState state = dbClient.getBackupState();
    state.setLastStarted(ZonedDateTime.now());
    dbClient.saveBackupState(state);

    try {
      callback.run();
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
      BackupState state = dbClient.getBackupState();
      if (state != null && state.getLastStarted() != null) {
        next = state.getLastStarted().plusMinutes(configuration.getIntervalInMinutes());
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

  private void notifyFinish() {
    blockingLatch.release();
  }
}
