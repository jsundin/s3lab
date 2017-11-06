package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.common.SimpleThreadFactory;
import ng3.common.ValuePair;
import ng3.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FileCopyBackupDriver extends AbstractBackupDriver {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final File path;
  private final int threads;

  @JsonCreator
  public FileCopyBackupDriver(
      @JsonProperty("path") File path,
      @JsonProperty("threads") Integer threads) {
    this.path = path;
    this.threads = threads == null || threads < 2 ? 1 : threads;
  }

  @Override
  protected AbstractBackupSessionNG openSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    return new FileCopyBackupSessionNG(dbClient, report, backupDirectories);
  }

  public class FileCopyBackupSessionNG extends AbstractBackupSessionNG {
    private final ExecutorService executor;
    private final Semaphore threadSemaphore;
    private final Map<UUID, ValuePair<File, String>> backupTargets = new HashMap<>();

    FileCopyBackupSessionNG(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
      super(dbClient, report, backupDirectories);
      executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopy"));
      threadSemaphore = new Semaphore(threads);
    }

    @Override
    protected void init() {
      super.init();
      for (BackupDirectory backupDirectory : backupDirectories) {
        UUID id = backupDirectory.getId();
        File directory = backupDirectory.getConfiguration().getDirectory();
        String storeAs = backupDirectory.getConfiguration().getStoreAs();

        if (storeAs == null) {
          backupTargets.put(id, new ValuePair<>(path, ""));
        } else {
          backupTargets.put(id, new ValuePair<>(new File(path, storeAs), directory.toString()));
        }
      }
    }

    @Override
    protected void finish() {
      super.finish();
      threadSemaphore.acquireUninterruptibly(threads);
      executor.shutdown();
      while (true) {
        try {
          executor.awaitTermination(999, TimeUnit.DAYS); // TODO: Settings
          break;
        } catch (InterruptedException ignored) {
          Thread.interrupted();
        }
      }
    }

    @Override
    protected void handleFile(BackupFile backupFile) {
      ValuePair<File, String> targetAndPrefix = backupTargets.get(backupFile.directoryId);
      if (targetAndPrefix == null) {
        logger.error("Could not find directory prefix '{}' in prepared map when handling file '{}' - this shouldn't happen", backupFile.directoryId, backupFile.file);
        report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
        report.getTargetReportWriter().failedFile();
        return;
      }

      File target = targetAndPrefix.getLeft();
      String prefix = targetAndPrefix.getRight();
      String fqfn = backupFile.file.toString();
      if (!fqfn.startsWith(prefix)) {
        logger.error("File '{}' should start with prefix '{}'", fqfn, prefix);
        report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
        report.getTargetReportWriter().failedFile();
        return;
      }
      fqfn = fqfn.substring(prefix.length());
      target = new File(target, fqfn);

      CopyFileTask copyFileTask = new CopyFileTask(backupFile.file, target);

      threadSemaphore.acquireUninterruptibly();
      executor.submit(() -> {
        try {
          Boolean result = copyFileTask.execute();
          if (result) {
            report.getTargetReportWriter().successfulFile();
          } else {
            report.getTargetReportWriter().failedFile();
          }
        } catch (Throwable error) {
          logger.error("Unhandled exception caught while processing '{}'", backupFile.file);
          logger.error("", error);
          report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
          report.getTargetReportWriter().failedFile();
        } finally {
          threadSemaphore.release();
        }
      });
    }
  }

  private class CopyFileTask {
    private final File src;
    private final File target;

    private CopyFileTask(File src, File target) {
      this.src = src;
      this.target = target;
    }

    private boolean execute() throws Exception {
      logger.info("COPY '{}' '{}'", src, target);
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return true;
    }
  }
}
