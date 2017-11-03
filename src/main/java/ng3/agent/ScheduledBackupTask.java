package ng3.agent;

import ng3.BackupPlan;
import ng3.common.BlockingLatch;
import ng3.common.ErrorCallback;
import ng3.common.ScheduledTask;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class ScheduledBackupTask extends ScheduledTask {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final UUID planId;
  private final DbClient dbClient;
  private final Configuration configuration;
  private final boolean reschedule;
  private final BlockingLatch blockingLatch;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(r, "BackupTask"));
  private final Runnable callback;

  ScheduledBackupTask(UUID planId, DbClient dbClient, Configuration configuration, boolean reschedule, BlockingLatch blockingLatch, Runnable callback, ErrorCallback errorCallback) {
    super(errorCallback);
    this.planId = planId;
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
    BackupPlan backupPlan = dbClient.getBackupPlan(planId);
    backupPlan.setLastStarted(ZonedDateTime.now());
    dbClient.saveBackupPlan(backupPlan);

    callback.run();

    if (!reschedule) {
      notifyFinish();
    }
    return reschedule;
  }

  @Override
  protected ScheduledFuture<?> scheduleFuture(boolean forceNow) {
    ZonedDateTime next = null;
    if (!forceNow) {
      BackupPlan backupPlan = dbClient.getBackupPlan(planId);
      if (backupPlan.getLastStarted() != null) {
        next = backupPlan.getLastStarted().plusMinutes(configuration.getIntervalInMinutes());
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
