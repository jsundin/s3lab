package ng3.agent;

import ng3.BackupDirectory;
import ng3.Settings;
import ng3.common.SimpleThreadFactory;
import ng3.db.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.FileTools;
import s5lab.configuration.FileRule;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

class FileScanner {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final DbClient dbClient;
  private final BackupReportWriter report;
  private final List<BackupDirectory> backupDirectories;

  FileScanner(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    this.dbClient = dbClient;
    this.report = report;
    this.backupDirectories = backupDirectories;
  }

  void scan() {
    report.getFileScannerReportWriter().setStartedAt(ZonedDateTime.now());

    CountDownLatch countDownLatch = new CountDownLatch(backupDirectories.size());
    BlockingQueue<FileScannerEvent> fileScannerEvents = new LinkedBlockingQueue<>();
    ThreadFactory threadFactory = new SimpleThreadFactory("FileScanner");
    for (BackupDirectory backupDirectory : backupDirectories) {
      Thread t = threadFactory.newThread(() -> {
        try {
          scanDirectory(fileScannerEvents, backupDirectory.getId(), backupDirectory.getConfiguration().getDirectory(), backupDirectory.getConfiguration().getRules());
        } finally {
          System.out.println("countdown");
          countDownLatch.countDown();
        }
      });
      t.start();
    }

    while (countDownLatch.getCount() != 0 || !fileScannerEvents.isEmpty()) {
      FileScannerEvent event;
      try {
        event = fileScannerEvents.poll(Settings.FILESCAN_POLL_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.interrupted();
        continue;
      }
      if (event == null) {
        continue;
      }

      scanFile(event.directoryId, event.file);
    }

    report.getFileScannerReportWriter().setFinishedAt(ZonedDateTime.now());
  }

  private void scanDirectory(BlockingQueue<FileScannerEvent> fileScannerEvents, UUID directoryId, File directory, List<FileRule> fileRules) {
    if (!directory.exists()) {
      logger.error("Directory '{}' does not exist", directory);
      report.addError("Directory '%s' does not exist", directory);
      return;
    }

    if (!directory.isDirectory()) {
      logger.error("Directory '{}' is not a directory'", directory);
      report.addError("Directory '%s' is not a directory", directory);
      return;
    }

    File[] files = directory.listFiles();
    if (files == null) {
      logger.error("Could not access directory '{}'", directory);
      report.addError("Could not access directory '%s'", directory);
      return;
    }

    for (File file : files) {
      report.getFileScannerReportWriter().foundFile();
      boolean accepted = true;
      for (FileRule fileRule : fileRules) {
        if (!fileRule.accept(file)) {
          accepted = false;
          break;
        }
      }
      if (!accepted) {
        report.getFileScannerReportWriter().rejectedFile();
        continue;
      }

      if (file.isDirectory()) {
        report.getFileScannerReportWriter().acceptedDirectory();
        scanDirectory(fileScannerEvents, directoryId, file, fileRules);
      } else if (file.isFile()) {
        report.getFileScannerReportWriter().acceptedFile();
        fileScannerEvents.add(new FileScannerEvent(directoryId, file));
      } else {
        logger.warn("Could not determine file type for '{}", file);
        report.addWarning("Could not determine file type for '%s'", file);
      }
    }
  }

  private void scanFile(UUID directoryId, File file) {
    StoredFile storedFile = dbClient.buildQuery("select file_id, last_modified from file where directory_id=? and filename=?")
            .withParam().uuidValue(1, directoryId)
            .withParam().fileValue(2, file)
            .executeQueryForObject(rs -> new StoredFile(rs.getUuid(1), rs.getTimestamp(2)));

    if (storedFile == null) {
      UUID fileId = UUID.randomUUID();
      ZonedDateTime lastModified = FileTools.lastModified(file);

      dbClient.buildQuery("insert into file (file_id, directory_id, filename, last_modified) values (?, ?, ?, ?)")
              .withParam().uuidValue(1, fileId)
              .withParam().uuidValue(2, directoryId)
              .withParam().fileValue(3, file)
              .withParam().timestampValue(4, lastModified)
              .executeUpdate();
      report.getFileScannerReportWriter().newFile();
    } else {
      ZonedDateTime lastModified = FileTools.lastModified(file);
      if (lastModified.isAfter(storedFile.lastModified)) {
        dbClient.buildQuery("update file set last_modified=? where file_id=?")
                .withParam().timestampValue(1, lastModified)
                .withParam().uuidValue(2, storedFile.id)
                .executeUpdate();
        report.getFileScannerReportWriter().updatedFile();
      }
    }
  }

  private class FileScannerEvent {
    private final UUID directoryId;
    private final File file;

    private FileScannerEvent(UUID directoryId, File file) {
      this.directoryId = directoryId;
      this.file = file;
    }
  }

  private class StoredFile {
    private final UUID id;
    private final ZonedDateTime lastModified;

    public StoredFile(UUID id, ZonedDateTime lastModified) {
      this.id = id;
      this.lastModified = lastModified;
    }
  }
}
