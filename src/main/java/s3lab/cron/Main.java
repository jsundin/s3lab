package s3lab.cron;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

public class Main {
  public static void main(String[] args) throws Exception {
    SchedulerFactory sf = new StdSchedulerFactory();
    Scheduler scheduler = sf.getScheduler();
    JobDetail job = JobBuilder.newJob(HelloJob.class).withIdentity("myJob").build();

    CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity("cronTrigger").withSchedule(CronScheduleBuilder.cronSchedule("0/20 * * * * ?")).build();

    scheduler.scheduleJob(job, trigger);
    scheduler.start();
  }

  public static class HelloJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
      System.out.println("HELLO!");
    }
  }
}
