package s4lab;

import s4lab.db.DbHandler;
import s4lab.db.FileRepository;
import s4lab.fs.*;

import java.util.Arrays;

public class Main {
  public static void main(String[] args) throws Exception {
    FileSystemScanner fs = new FileSystemScanner();
    DbHandler dbHandler = new DbHandler();
    FileRepository fileRepository = new FileRepository(dbHandler);

    BackupAgent backupAgent = new BackupAgent(fileRepository);
    backupAgent.start();

    fs.scan(backupAgent.getFileScanQueue(),
            Arrays.asList(
                    //new DirectoryConfiguration("/home/jsundin/tmp/filemontest/", new PathPrefixExcludeRule("/home/jsundin/tmp/filemontest/exclude"))
                    new DirectoryConfiguration("/home/jsundin/")
            ),
            new SymlinkExcludeRule(),
            new HiddenFileExcludeRule()
    );

    Thread.sleep(2000);

    backupAgent.finish(true);
  }
/*

  public static void scanAndSave() throws SQLException {
    XYZ xyz = new XYZ();
    xyz.start();

    FileSystemScanner fs = new FileSystemScanner(xyz.runner.fileQueue);
    fs.scan(
            Arrays.asList(
                    new DirectoryConfiguration("/home/jsundin/", new PathPrefixExcludeRule("/home/jsundin/tmp/filemonlab/exclude/"))
                    //new DirectoryConfiguration("/home/jsundin/tmp/filemontest/", new PathPrefixExcludeRule("/home/jsundin/tmp/filemontest/exclude/"))
            ),
            new SymlinkExcludeRule(),
            new HiddenFileExcludeRule(),
            new PathSuffixExcludeRule(".log")
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
