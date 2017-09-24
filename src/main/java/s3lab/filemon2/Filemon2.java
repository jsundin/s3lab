package s3lab.filemon2;

import org.quartz.*;

public class Filemon2 {
  public static void main(String[] args) throws Exception {
    DbAdapter dbAdapter = new DbAdapter();
    BackupAgent agent = new BackupAgent(dbAdapter);
    SchedulerProxy schedulerProxy = new SchedulerProxy(agent);
    schedulerProxy.addJob("cleanupJob", CleanupJob.class, "0/10 * * * * ?");

    FileScanner fs = new FileScanner(agent, dbAdapter);
    fs.addInclude("/home/jsundin/tmp/filemontest");
    fs.addExclude("/home/jsundin/tmp/filemontest/exclude");
    fs.scan();
    System.out.println("Scanning complete...");

    agent.start();
    schedulerProxy.start();
    Thread.sleep(20000);
    agent.finish();
    schedulerProxy.finish();
  }

  public static class CleanupJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
      System.out.println("Cleaning up!");
      BackupAgent backupAgent;
      SchedulerContext schedulerContext;

      try {
        schedulerContext = context.getScheduler().getContext();
      } catch (SchedulerException e) {
        throw new RuntimeException(e); // we can't recover from this
      }

      Object agentObj = schedulerContext.get(SchedulerProxy.BACKUP_AGENT_KEY);
      if (agentObj == null || !(agentObj instanceof BackupAgent)) {
        throw new RuntimeException("Backup agent is not an actual backup agent: " + agentObj.getClass()); // we can't recover
      }
      backupAgent = (BackupAgent) agentObj;
      backupAgent.housekeeping();
    }
  }
}
