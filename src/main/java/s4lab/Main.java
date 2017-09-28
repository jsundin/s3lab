package s4lab;

import s4lab.conf.Configuration;
import s4lab.conf.ConfigurationReader;
import s4lab.db.DbHandler;
import s4lab.db.FileRepository;
import s4lab.fs.FileSystemScanner;
import s4lab.fs.rules.ExcludeOldFilesRule;
import s4lab.fs.rules.ExcludeRule;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class Main {
  public static void main(String[] args) throws Exception {
    new Main().run();
  }

  //private static final String CONFIG = "/config3.json";
  private static final String CONFIG = "/config2.json";

  public void run() throws Exception {
    Configuration config = new ConfigurationReader().readConfiguration(getClass().getResourceAsStream(CONFIG));

    DbHandler dbHandler = new DbHandler();
    dbHandler.start();
    FileSystemScanner fs = new FileSystemScanner(dbHandler);
    FileRepository fileRepository = new FileRepository(dbHandler);

    BackupAgent backupAgent = new BackupAgent(fileRepository);
    backupAgent.start();

    ZonedDateTime lastScan = dbHandler.readStateInformation().getLastScan();
    ExcludeRule[] systemExcludeRules = new ExcludeRule[lastScan == null ? 0 : 1];
    if (lastScan != null) {
      systemExcludeRules[0] = new ExcludeOldFilesRule(lastScan);
    }
    fs.scan(backupAgent, config.getDirectoryConfigurations(), systemExcludeRules);
/*

            Arrays.asList(
                new DirectoryConfiguration("/home/johdin/tmp/backuplab/")
            ),
*/
/*
            Arrays.asList(
                    //new DirectoryConfiguration("/home/jsundin/tmp/filemontest/", new ExcludePathPrefixRule("/home/jsundin/tmp/filemontest/exclude"))
                    new DirectoryConfiguration("/home/jsundin/",
                            new ExcludePathPrefixRule("/home/jsundin/tmp/squash/"),
                            new ExcludePathPrefixRule("/home/jsundin/Android/"),
                            new ExcludePathPrefixRule("/home/jsundin/AndroidStudioProjects/"),
                            new ExcludePathPrefixRule("/home/jsundin/apps/")),
                    new DirectoryConfiguration("/aleska/video")
            ),
*//*

            new ExcludeOldFilesRule(latestModified),
            new ExcludeSymlinksRule(),
            new ExcludeHiddenFilesRule()
    );
*/

    //Thread.sleep(2000000);

    backupAgent.finish();
    dbHandler.finish();
  }
/*

  public static void scanAndSave() throws SQLException {
    XYZ xyz = new XYZ();
    xyz.start();

    FileSystemScanner fs = new FileSystemScanner(xyz.runner.fileQueue);
    fs.scan(
            Arrays.asList(
                    new DirectoryConfiguration("/home/jsundin/", new ExcludePathPrefixRule("/home/jsundin/tmp/filemonlab/exclude/"))
                    //new DirectoryConfiguration("/home/jsundin/tmp/filemontest/", new ExcludePathPrefixRule("/home/jsundin/tmp/filemontest/exclude/"))
            ),
            new ExcludeSymlinksRule(),
            new ExcludeHiddenFilesRule(),
            new ExcludePathSuffixRule(".log")
    );

    xyz.finish(true);
  }

  private static class XYZ {
    private Runner runner = new Runner();

    public void start() {
      runner.start();
    }

    public void finish(boolean graceful) {
      if (!graceful || runner.fileQueue.isEmpty()) {
        runner.finished = true;
        runner.interrupt();
      }
    }

    private class Runner extends Thread {
      private final BlockingQueue<File> fileQueue = new LinkedBlockingQueue<>();
      private volatile boolean finished = false;

      @Override
      public void run() {
        do {
          try {
            File f = fileQueue.take();
            System.out.println(f);
          } catch (InterruptedException ignored) {}
        } while (!finished);
      }
    }
  }
*/
}
