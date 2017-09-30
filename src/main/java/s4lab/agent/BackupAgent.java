package s4lab.agent;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.conf.Configuration;
import s4lab.conf.ConfigurationReader;
import s4lab.conf.Settings;
import s4lab.db.DbHandler;
import s4lab.target.DevNullBackupTarget;

import java.io.File;
import java.io.IOException;

public class BackupAgent {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public void start() {

  }

  public void finish() {

  }

  public static void main(String[] args) throws Exception {
    new BackupAgent().run();
  }

  private void restart(DbHandler dbh) throws IOException {
    File file = new File("/tmp/backuptarget");
    FileUtils.deleteDirectory(file);
    dbh.buildQuery("delete from file_version").executeUpdate();
    dbh.buildQuery("delete from file").executeUpdate();
    dbh.buildQuery("update directory_config set last_scan=null").executeUpdate();
  }

  public void run() throws Exception {
    long t0 = System.currentTimeMillis();

    // TODO: vid uppstart måste vi köra "update file_version set upload_state=null where upload_state<>'FINISHED'
    //       annars startas inte avbrutna uppladdningar om
    DbHandler dbh = new DbHandler(Settings.JDBC_URL, Settings.JDBC_USERNAME, Settings.JDBC_PASSWORD, Settings.JDBC_POOL_SIZE);
    dbh.start();

    //restart(dbh);

    Configuration config = new ConfigurationReader().readConfiguration(getClass().getResourceAsStream("/config3.json"));

    //BackupTarget backupTarget = new LocalDirectoryBackupTarget(dbh, new File("/tmp/backuptarget"));
    DevNullBackupTarget backupTarget = new DevNullBackupTarget(dbh, 1);

    FileUploadManager fileUploadManager = new FileUploadManager(dbh, Settings.UPLOAD_THREADS, backupTarget);
    fileUploadManager.start();

    new FileScanner(dbh).scan(config.getDirectoryConfigurations(), false);

    fileUploadManager.finish();

    dbh.finish();

    logger.info("BackupAgent finished in {}ms", (System.currentTimeMillis() - t0));
    logger.info("/dev/null-stats: {} files and a total of {}mb", backupTarget.getTotalFiles(), backupTarget.getTotalSize() / (1024 * 1024));
  }
}
