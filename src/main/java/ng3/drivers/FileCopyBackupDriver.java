package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.common.SimpleThreadFactory;
import ng3.db.DbClient;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class FileCopyBackupDriver implements BackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File path;
  private final boolean createPathIfNeeded;

  @JsonCreator
  public FileCopyBackupDriver(
          @JsonProperty("path") File path,
          @JsonProperty("create-if-needed") boolean createPathIfNeeded) {
    this.path = path;
    this.createPathIfNeeded = createPathIfNeeded;
  }

  @Override
  public BackupSession startSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, UUID planId) {
    dbClient.buildQuery("update file set upload_started=null").executeUpdate(); // TODO: bort :)

    BackupTask task = new BackupTask(dbClient, report, backupDirectories, planId);
    new SimpleThreadFactory("FileCopy").newThread(task).start();
    return new FileCopyBackupSession(task);
  }

  public class FileCopyBackupSession implements BackupSession {
    private final BackupTask task;

    public FileCopyBackupSession(BackupTask task) {
      this.task = task;
    }

    @Override
    public void finish() {
      task.sessionFinished.release();
      task.finished.acquireUninterruptibly();
    }
  }

  private class BackupTask implements Runnable {
    private final DbClient dbClient;
    private final UUID planId;
    private final Semaphore finished = new Semaphore(0);
    private final Semaphore sessionFinished = new Semaphore(0);
    private final BackupReportWriter report;
    private final List<BackupDirectory> backupDirectories;
    private final Map<UUID, File> targetDirectories = new HashMap<>();
    private final Map<UUID, String> stripDirectories = new HashMap<>();

    private BackupTask(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, UUID planId) {
      this.dbClient = dbClient;
      this.report = report;
      this.backupDirectories = backupDirectories;
      this.planId = planId;
    }

    @Override
    public void run() {
      try {
        report.getTargetReportWriter().setStartedAt(ZonedDateTime.now());
        setupDirectories();
        loop();
        report.getTargetReportWriter().setFinishedAt(ZonedDateTime.now());
      } catch (Throwable error) {
        logger.error("Unhandled error for plan '{}'", planId);
        logger.error("", error);
        report.addError("Unhandled error in backup driver, see system logs for details");
      } finally {
        finished.release();
      }
    }

    private void setupDirectories() {
      for (BackupDirectory backupDirectory : backupDirectories) {
        File srcDirectory = backupDirectory.getConfiguration().getDirectory();
        File targetDirectory = path;
        if (backupDirectory.getConfiguration().getStoreAs() != null) {
          targetDirectory = new File(targetDirectory, backupDirectory.getConfiguration().getStoreAs());
        }

        stripDirectories.put(backupDirectory.getId(), srcDirectory.toString());
        targetDirectories.put(backupDirectory.getId(), targetDirectory);
      }
    }

    private void loop() {
      while (true) {
        XYZ xyz = dbClient.buildQuery("select f.file_id, f.filename, f.deleted, f.directory_id from file f join directory d on d.directory_id=f.directory_id where d.plan_id=? and upload_started is null or (upload_finished is not null and upload_started>upload_finished) or (upload_started is not null and last_modified>upload_started) fetch next 1 rows only")
                .withParam().uuidValue(1, planId)
                .executeQueryForObject(rs -> new XYZ(rs.getUuid(1), rs.getFile(2), rs.getBoolean(3), rs.getUuid(4)));
        if (xyz == null) {
          if (sessionFinished.availablePermits() >= 0) {
            break;
          }
          try {
            Thread.sleep(200); // TODO: Settings
          } catch (InterruptedException e) {
            Thread.interrupted();
          }
          continue;
        }
        report.getTargetReportWriter().processedFile();
        if (copyFile(xyz.file, xyz.directoryId)) {
          report.getTargetReportWriter().successfulFile();
        } else {
          report.getTargetReportWriter().failedFile();
        }
        dbClient.buildQuery("update file set upload_started=? where file_id=?")
                .withParam().timestampValue(1, ZonedDateTime.now())
                .withParam().uuidValue(2, xyz.id)
                .executeUpdate();
      }
    }

    private File getTargetFile(File src, UUID directoryId) {
      if (!targetDirectories.containsKey(directoryId)) {
        report.addError("Could not determine target filename for '%s' - could not find target directory", src);
        logger.error("Could not determine target filename for '{}' - could not find target directory", src);
        return null;
      }
      String src_str = src.toString();
      if (stripDirectories.containsKey(directoryId)) {
        if (!src_str.startsWith(stripDirectories.get(directoryId))) {
          report.addError("Could not determine target filename for '%s' - filename doesn't start with '%s'", src, stripDirectories.get(directoryId));
          logger.error("Could not determine target filename for '{}' - filename doesn't start with '{}'", src, stripDirectories.get(directoryId));
          return null;
        }
        src_str = src_str.substring(stripDirectories.get(directoryId).length());
      }

      return new File(targetDirectories.get(directoryId), src_str);
    }

    private boolean copyFile(File src, UUID directoryId) {
      File targetFile = getTargetFile(src, directoryId);
      if (targetFile == null) {
        return false;
      }
      if (!targetFile.getParentFile().exists()) {
        if (!targetFile.getParentFile().mkdirs()) {
          logger.error("Could not create directory '{}'", targetFile.getParentFile());
          report.addError("Could not create directory '%s'", targetFile.getParentFile());
          return false;
        }
      }

      try (FileInputStream fis = new FileInputStream(src)) {
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
          IOUtils.copy(fis, fos);
        }
      } catch (IOException e) {
        logger.error("Could not copy file '{}' to '{}'", src, targetFile);
        logger.error("", e);
        report.addError("Could not copy file '%s' to '%s' - see system logs for details", src, targetFile);
        return false;
      }
      return true;
    }
  }

  private class XYZ {
    private final UUID id;
    private final File file;
    private final boolean deleted;
    private final UUID directoryId;

    private XYZ(UUID id, File file, boolean deleted, UUID directoryId) {
      this.id = id;
      this.file = file;
      this.deleted = deleted;
      this.directoryId = directoryId;
    }
  }
}
