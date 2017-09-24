package s3lab.filemon3;

import org.quartz.*;

import java.nio.file.Paths;
import java.util.Arrays;

public class Runner {
  public static void main(String[] args) throws Exception {
    //DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
    //Class.forName("org.apache.derby.jdbc.ClientDriver");

    DbAdapter dbAdapter = new DbAdapter();
    //dbAdapter.start();
    //dbAdapter.initTables();
    BackupAgent backupAgent = new BackupAgent(dbAdapter);
    SchedulerProxy scheduler = new SchedulerProxy(backupAgent);
    scheduler.addJob("cleanupJob", CleanupJob.class, "0/10 * * * * ?");

    InitialFileScanner initialFileScanner = new InitialFileScanner(dbAdapter, backupAgent);
    //initialFileScanner.scan(Arrays.asList(Paths.get("/home/jsundin/tmp/filemontest")), Arrays.asList(Paths.get("/home/jsundin/tmp/filemontest/exclude")));
    initialFileScanner.scan(Arrays.asList(Paths.get("/home/jsundin")), Arrays.asList(Paths.get("/home/jsundin/.cache"), Paths.get("/home/jsundin/.gconf"), Paths.get("/home/jsundin/tmp/squash")));
    //initialFileScanner.scan(Arrays.asList(Paths.get("/home/jsundin")), Arrays.asList(Paths.get("/home/jsundin/.cache"), Paths.get("/home/jsundin/tmp/squash")));

    backupAgent.start();
    scheduler.start();

    Thread.sleep(20000);

    scheduler.finish();
    backupAgent.finish();
    //dbAdapter.finish();
  }

  public static class CleanupJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
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
      backupAgent.cleanup();
    }
  }
}
