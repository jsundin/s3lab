package s4lab.agent;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.conf.Configuration;
import s4lab.conf.ConfigurationReader;
import s4lab.db.DbHandler;

import java.io.File;
import java.util.List;

public class BackupAgent {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public void start() {

  }

  public void finish() {

  }

  public static void main(String[] args) throws Exception {
    new BackupAgent().run();
  }

  public void run() throws Exception {
    // TODO: vid uppstart måste vi köra "update file_version set upload_state=null where upload_state<>'FINISHED'
    //       annars startas inte avbrutna uppladdningar om
    DbHandler dbh = new DbHandler();
    dbh.start();

    if (Math.abs(-1) < 0) {
      File file = new File("/tmp/backuptarget");
      FileUtils.deleteDirectory(file);

      dbh.buildQuery("delete from file_version").executeUpdate();
      dbh.buildQuery("delete from file").executeUpdate();
      dbh.buildQuery("update directory_config set last_scan=null").executeUpdate();

      dbh.buildQuery("update file_version set upload_state=null")
              .executeUpdate();
    }

    Configuration config = new ConfigurationReader().readConfiguration(getClass().getResourceAsStream("/config2.json"));

    FileUploadManager fileUploadManager = new FileUploadManager(dbh, 2);
    fileUploadManager.start();
    new FileScanner(dbh).scan(config.getDirectoryConfigurations(), false);

    fileUploadManager.finish();

    List<String> states = dbh.buildQuery("select upload_state from file_version")
            .executeQuery(rs -> rs.getString(1));
    dbh.finish();
  }
}
