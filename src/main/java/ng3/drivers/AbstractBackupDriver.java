package ng3.drivers;

import ng3.BackupDirectory;
import ng3.Settings;
import ng3.agent.BackupReportWriter;
import ng3.common.SimpleThreadFactory;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

abstract public class AbstractBackupDriver implements BackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public final BackupSessionNG startSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    dbClient.buildQuery("update file set upload_finished=null").executeUpdate(); // TODO: BORT!
    dbClient.buildQuery("update file set upload_started=null where upload_finished is null")
        .executeUpdate();
    AbstractBackupSession session = openSession(dbClient, configuration, report, backupDirectories);
    new SimpleThreadFactory("BackupDriver").newThread(session).start();
    logger.info("Session started");
    return session;
  }

  abstract protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories);

  protected final char[] getPassword(String encryptionKey, Configuration configuration, BackupReportWriter report) {
    char[] password = null;
    if (encryptionKey != null) {
      password = configuration.getSecrets().get(encryptionKey);
      if (password == null) {
        logger.error("Could not find password for encryption key '{}'", encryptionKey);
        report.addError("Could not find password for encryption key - see system logs for details");
        throw new RuntimeException("Could not find password for encryption key");
      }
    }
    return password;
  }

  abstract public class AbstractBackupSession implements BackupDriver.BackupSessionNG, Runnable {
    protected final DbClient dbClient;
    protected final BackupReportWriter report;
    protected final List<BackupDirectory> backupDirectories;
    private final List<UUID> directoryIds;
    private final Semaphore sessionSemaphore = new Semaphore(0);
    private final Semaphore taskSemaphore = new Semaphore(0);

    AbstractBackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
      this.dbClient = dbClient;
      this.report = report;
      this.backupDirectories = backupDirectories;
      directoryIds = backupDirectories.stream().map(BackupDirectory::getId).collect(Collectors.toList());
    }

    @Override
    public final void endSession() {
      sessionSemaphore.release();
      taskSemaphore.acquireUninterruptibly();
      logger.info("Session ended");
    }

    abstract protected void handleFile(BackupFile backupFile);

    protected void init() {
    }

    protected void finish() {
    }

    @Override
    public final void run() {
      logger.info("Backup running for directories: {}", backupDirectories.stream().map(v -> v.getConfiguration().getDirectory().toString()).collect(Collectors.joining(", ", "'", "'")));
      report.getTargetReportWriter().setStartedAt(ZonedDateTime.now());

      try {
        runInternal();
      } catch (Throwable t) {
        logger.error("Uncaught exception", t);
        report.addError("Internal error - see system logs for details");
      } finally {
        report.getTargetReportWriter().setFinishedAt(ZonedDateTime.now());
        taskSemaphore.release();
      }
    }

    private void runInternal() {
      init();

      while (true) {
        BackupFile file = getAndMarkNextFileForBackup();
        if (file == null) {
          if (sessionSemaphore.availablePermits() > 0) {
            break;
          }
          try {
            Thread.sleep(Settings.BACKUP_DRIVER_POLL_TIMEOUT_IN_MS);
          } catch (InterruptedException e) {
            Thread.interrupted();
          }
          continue;
        }

        report.getTargetReportWriter().processedFile();
        handleFile(file);
      }

      finish();
    }

    private BackupFile getAndMarkNextFileForBackup() {
      BackupFile backupFile = dbClient.buildQuery(
          "select file_id, filename, deleted, directory_id, last_modified from file " +
              "where directory_id in (" + IntStream.range(0, directoryIds.size()).mapToObj(v -> "?").collect(Collectors.joining(",")) + ") " +
              "and upload_started is null or (upload_finished is not null and upload_started>upload_finished) or (upload_started is not null and last_modified>upload_started) " +
              "fetch next 1 rows only")
          .withParam().uuidValues(1, directoryIds)
          .executeQueryForObject(rs -> new BackupFile(rs.getUuid(1), rs.getFile(2), rs.getBoolean(3), rs.getUuid(4), rs.getTimestamp(5)));

      if (backupFile != null) {
        dbClient.buildQuery("update file set upload_started=?, upload_finished=null where file_id=?")
            .withParam().timestampValue(1, ZonedDateTime.now())
            .withParam().uuidValue(2, backupFile.id)
            .executeUpdate();
      }
      return backupFile;
    }
  }

  protected class BackupFile {
    protected final UUID id;
    protected final File file;
    protected final boolean deleted;
    protected final UUID directoryId;
    protected final ZonedDateTime lastModified;

    private BackupFile(UUID id, File file, boolean deleted, UUID directoryId, ZonedDateTime lastModified) {
      this.id = id;
      this.file = file;
      this.deleted = deleted;
      this.directoryId = directoryId;
      this.lastModified = lastModified;
    }
  }
}
