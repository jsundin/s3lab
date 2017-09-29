package s4lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.db.DbHandler;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BackupAgent {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public void start() {

  }

  public void finish() {

  }

  /*public static void main(String[] args) throws Exception {
    old_FilescanThread.FilescanHandle fsh = new old_FilescanThread(UUID.randomUUID()).start();
    fsh.fileFound(new File("/etc/passwd"));
    fsh.finish();
  }*/
}
