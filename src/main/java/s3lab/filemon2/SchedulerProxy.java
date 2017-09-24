package s3lab.filemon2;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class SchedulerProxy {
  public static final String BACKUP_AGENT_KEY = "backupAgent";
  private final Scheduler scheduler;

  public SchedulerProxy(BackupAgent backupAgent) {
    try {
      SchedulerFactory schedulerFactory = new StdSchedulerFactory();
      scheduler = schedulerFactory.getScheduler();
      scheduler.getContext().put(BACKUP_AGENT_KEY, backupAgent);
    } catch (SchedulerException e) {
      throw new RuntimeException(e); // we can't recover from this
    }
  }

  public void addJob(String identity, Class<? extends org.quartz.Job> clazz, String cronExpression) throws SchedulerException {
    JobDetail job = newJob(clazz)
            .withIdentity(identity)
            .build();
    Trigger trigger = newTrigger()
            .withIdentity(identity + "Trigger")
            .withSchedule(cronSchedule(cronExpression)).build();
    scheduler.scheduleJob(job, trigger);
  }

  public void start() throws SchedulerException {
    scheduler.start();
  }

  public void finish() throws SchedulerException {
    scheduler.shutdown(true);
  }
}
