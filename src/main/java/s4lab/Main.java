package s4lab;

import s4lab.db.DbHandler;
import s4lab.db.FileRepository;
import s4lab.fs.DirectoryConfiguration;
import s4lab.fs.FileScanner;
import s4lab.fs.PathPrefixExcludeRule;
import s4lab.fs.SymlinkExcludeRule;

import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    scanAndSave();
/*

    BackupAgent backupAgent = new BackupAgent();
    backupAgent.start();

    Thread.sleep(2000);

    backupAgent.finish();
    */
  }

  public static void scanAndSave() throws SQLException {
    FileScanner fs = new FileScanner();
    List<File> files = fs.scan(
            Arrays.asList(
                    //new DirectoryConfiguration("/home/jsundin/", new PathPrefixExcludeRule("/home/jsundin/tmp/filemonlab/exclude/"))
                    new DirectoryConfiguration("/home/jsundin/tmp/filemontest/", new PathPrefixExcludeRule("/home/jsundin/tmp/filemontest/exclude/"))
            ),
            new SymlinkExcludeRule()
    );

    DbHandler dbh = new DbHandler();
    FileRepository fr = new FileRepository(dbh);

    fr.deleteAndInsertFiles(files);
  }
}
