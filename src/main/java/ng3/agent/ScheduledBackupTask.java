package ng3.agent;

import ng3.common.ScheduledTask;
import ng3.common.SimpleThreadFactory;
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
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new SimpleThreadFactory("BackupTask"));
  private final boolean runOnce;
  private final Controller controller;

  ScheduledBackupTask(Controller controller, boolean runOnce) {
    super(controller::onError);
    this.runOnce = runOnce;
    this.controller = controller;
  }

  void shutdown() {
    cancel();
    scheduler.shutdown();
  }

  @Override
  protected boolean performTask() {
    controller.backupTaskStarted();

    controller.executeJob();

    if (runOnce) {
      controller.schedulingFinished();
    }
    return !runOnce;
  }

  @Override
  protected ScheduledFuture<?> scheduleFuture(boolean forceNow) {
    ZonedDateTime next = forceNow ? null : controller.getNextExecutionTime();

    logger.info("Next backup scheduled " + (next == null ? "NOW" : "at " + next));
    if (next == null) {
      return scheduler.schedule(this, 1, TimeUnit.MILLISECONDS);
    } else {
      long millis = ChronoUnit.MILLIS.between(ZonedDateTime.now(), next);
      return scheduler.schedule(this, millis, TimeUnit.MILLISECONDS);
    }
  }

  public interface Controller {
    void backupTaskStarted();
    ZonedDateTime getNextExecutionTime();
    void executeJob();
    void schedulingFinished();
    void onError(Throwable t);
  }
}
