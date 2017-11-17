package ng3.drivers.filecopy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.common.CryptoUtils;
import ng3.common.SimpleThreadFactory;
import ng3.common.ValuePair;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import ng3.drivers.AbstractBackupDriver;
import ng3.drivers.VersioningDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FileCopyBackupDriver extends AbstractBackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  public static final String INFORMAL_NAME = "file-copy";
  final static String FILE_PREFIX = "$";
  final static String META_EXTENSION = ".meta";
  final static String DELETED_EXTENSION = ",DELETED";
  private final File path;
  private final int threads;
  private final boolean compress;
  private final String encryptionKey;

  @JsonCreator
  public FileCopyBackupDriver(
      @JsonProperty("path") File path,
      @JsonProperty("threads") Integer threads,
      @JsonProperty("compress") boolean compress,
      @JsonProperty("encrypt-with") String encryptionKey) {
    this.path = path;
    this.threads = threads == null || threads < 2 ? 1 : threads;
    this.compress = compress;
    this.encryptionKey = encryptionKey;
  }

  @Override
  public String getInformalName() {
    return INFORMAL_NAME;
  }

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    char[] password = getPassword(encryptionKey, configuration, report);
    return new BackupSession(dbClient, report, backupDirectories, threads, password);
  }

  @Override
  public VersioningDriver getVersioningDriver() throws UnsupportedOperationException {
    return new FileCopyVersioningDriver(path, threads);
  }

  public class BackupSession extends AbstractBackupDriver.AbstractBackupSession {
    private final ExecutorService executor;
    private final Semaphore threadSemaphore;
    private final Map<UUID, ValuePair<File, String>> backupTargets;
    private final char[] encryptionPassword;
    private final int threads;

    BackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, int threads, char[] encryptionPassword) {
      super(dbClient, report, backupDirectories);
      executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopy"));
      threadSemaphore = new Semaphore(threads);
      this.threads = threads;
      this.encryptionPassword = encryptionPassword;

      backupTargets = new HashMap<>();
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
          executor.awaitTermination(999, TimeUnit.DAYS); // TODO: -> Settings
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
      target = new File(target.getParent(), FILE_PREFIX + target.getName());

      CopyFileTask copyFileTask;
      if (encryptionPassword == null) {
        copyFileTask = new CopyFileTask(backupFile.file, target, compress);
      } else {
        byte[] salt = CryptoUtils.generateSalt();
        Key key = CryptoUtils.generateKey(encryptionPassword, salt);
        copyFileTask = new CopyFileTask(backupFile.file, target, compress, key, salt);
      }

      threadSemaphore.acquireUninterruptibly();
      executor.submit(() -> {
        try {
          if (copyFileTask.execute()) {
            report.getTargetReportWriter().successfulFile();
            uploadFinished(backupFile);
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
}
