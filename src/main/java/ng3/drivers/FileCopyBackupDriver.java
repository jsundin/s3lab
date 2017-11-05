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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FileCopyBackupDriver implements BackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File path;
  private final int threads;

  @JsonCreator
  public FileCopyBackupDriver(
          @JsonProperty("path") File path,
          @JsonProperty("threads") Integer threads) {
    this.path = path;
    if (threads == null || threads < 2) {
      this.threads = 1;
    } else {
      this.threads = threads;
    }
  }

  @Override
  public BackupSession startSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    //dbClient.buildQuery("update file set upload_finished=null").executeUpdate(); // TODO: bort :)

    dbClient.buildQuery("update file set upload_started=null where upload_finished is null") // restart all aborted uploads
            .executeUpdate();

    BackupTask task = new BackupTask(dbClient, report, backupDirectories);
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

  private class BackupTask extends AbstractBackupDriver implements Runnable {
    private final DbClient dbClient;
    private final Semaphore finished = new Semaphore(0);
    private final Semaphore sessionFinished = new Semaphore(0);
    private final BackupReportWriter report;
    private final List<BackupDirectory> backupDirectories;
    private final Map<UUID, File> targetDirectories = new HashMap<>();
    private final Map<UUID, String> stripDirectories = new HashMap<>();

    private BackupTask(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
      super(dbClient);
      this.dbClient = dbClient;
      this.report = report;
      this.backupDirectories = backupDirectories;
    }

    @Override
    public void run() {
      ExecutorService executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopy"));
      try {
        report.getTargetReportWriter().setStartedAt(ZonedDateTime.now());
        setupDirectories();
        loop(executor);
        executor.shutdown();
        awaitTermination(executor);
        report.getTargetReportWriter().setFinishedAt(ZonedDateTime.now());
      } catch (Throwable error) {
        logger.error("Unhandled error", error);
        report.addError("Unhandled error in backup driver, see system logs for details");
        executor.shutdownNow();
        awaitTermination(executor);
      } finally {
        finished.release();
      }
    }

    private void awaitTermination(ExecutorService executor) {
      while (true) {
        try {
          executor.awaitTermination(999, TimeUnit.DAYS); // TODO: -> Settings
          break;
        } catch (InterruptedException ignored) {
          Thread.interrupted();
        }
      }
    }

    private  void loop(ExecutorService executor) {
      Semaphore semaphore = new Semaphore(threads);
      while (true) {
        List<BackupFile> backupFiles = getNextFiles(threads, backupDirectories.stream().map(BackupDirectory::getId).collect(Collectors.toList()));
        if (backupFiles.isEmpty()) {
          if (sessionFinished.availablePermits() > 0) {
            break;
          }
          try {
            Thread.sleep(200); // TODO: Settings
          } catch (InterruptedException e) {
            Thread.interrupted();
          }
          continue;
        }

        report.getTargetReportWriter().processedFiles(backupFiles.size());
        for (BackupFile backupFile : backupFiles) {
          File target = getTargetFile(backupFile);

          semaphore.acquireUninterruptibly();
          CopyFileTask task = new CopyFileTask(backupFile.file, target, report, () -> {
            dbClient.buildQuery("update file set upload_finished=? where file_id=?")
                    .withParam().timestampValue(1, ZonedDateTime.now())
                    .withParam().uuidValue(2, backupFile.id)
                    .executeUpdate();
          }, semaphore::release);
          executor.submit(task);
        }
      }
      semaphore.acquireUninterruptibly(threads);
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

    private File getTargetFile(BackupFile backupFile) {
      if (!targetDirectories.containsKey(backupFile.directoryId)) {
        report.addError("Could not determine target filename for '%s' - could not find target directory", backupFile.file);
        logger.error("Could not determine target filename for '{}' - could not find target directory", backupFile.file);
        return null;
      }
      String src_str = backupFile.file.toString();
      if (stripDirectories.containsKey(backupFile.directoryId)) {
        if (!src_str.startsWith(stripDirectories.get(backupFile.directoryId))) {
          report.addError("Could not determine target filename for '%s' - filename doesn't start with '%s'", backupFile.file, stripDirectories.get(backupFile.directoryId));
          logger.error("Could not determine target filename for '{}' - filename doesn't start with '{}'", backupFile.file, stripDirectories.get(backupFile.directoryId));
          return null;
        }
        src_str = src_str.substring(stripDirectories.get(backupFile.directoryId).length());
      }

      File rawTarget = new File(targetDirectories.get(backupFile.directoryId), src_str);
      File target = rawTarget;
      File deletedTarget = new File(rawTarget.toString() + DELETED_MARKER);
      int n = 1;
      while (target.exists() || deletedTarget.exists()) {
        target = new File(rawTarget.toString() + "," + n);
        deletedTarget = new File(rawTarget.toString() + "," + n + DELETED_MARKER);
        n++;
      }
      return target;
    }
  }

  private final static String DELETED_MARKER = ",DELETED";

  private class CopyFileTask implements Runnable {
    private final File src;
    private final File target;
    private final BackupReportWriter report;
    private final Runnable successCallback;
    private final Runnable finishedCallback;

    private CopyFileTask(File src, File target, BackupReportWriter report, Runnable successCallback, Runnable finishedCallback) {
      this.src = src;
      this.target = target;
      this.report = report;
      this.successCallback = successCallback;
      this.finishedCallback = finishedCallback;
    }

    @Override
    public void run() {
      try {
        if (internalRun()) {
          report.getTargetReportWriter().successfulFile();
          successCallback.run();
        } else {
          report.getTargetReportWriter().failedFile();
        }
      } finally {
        finishedCallback.run();
      }
    }

    private boolean internalRun() {
      File parent = target.getParentFile();
      if (!parent.exists()) {
        if (!parent.mkdirs()) {
          logger.error("Could not create directory '{}'", parent);
          report.addError("Could not create directory '%s'", parent);
          return false;
        }
      }
      if (src.exists()) {
        return copy();
      } else {
        return delete();
      }
    }

    private boolean delete() {
      File deletedTarget = new File(target.toString() + DELETED_MARKER);
      try (FileOutputStream fos = new FileOutputStream(deletedTarget)) {

      } catch (IOException e) {
        logger.error("Could not mark file '{}' as deleted in '{}'", src, deletedTarget);
        logger.error("", e);
        report.addError("Could not mark file '%s' as deleted in '%s' - see system logs for details", src, deletedTarget);
        return false;
      }
      return true;
    }

    private boolean copy() {
      try (FileInputStream fis = new FileInputStream(src)) {
        try (FileOutputStream fos = new FileOutputStream(target)) {
          IOUtils.copy(fis, fos);
        }
      } catch (IOException e) {
        logger.error("Could not copy file '{}' to '{}'", src, target);
        logger.error("", e);
        report.addError("Could not copy file '%s' to '%s' - see system logs for details", src, target);
        return false;
      }
      return true;
    }
  }
}
