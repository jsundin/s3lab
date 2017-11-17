package ng3.drivers.archive;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.common.ValuePair;
import ng3.conf.Configuration;
import ng3.conf.SizeToBytesDeserializer;
import ng3.db.DbClient;
import ng3.drivers.AbstractBackupDriver;
import ng3.drivers.VersioningDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author johdin
 * @since 2017-11-06
 */
public class ArchiveBackupDriver extends AbstractBackupDriver {
  public static final String INFORMAL_NAME = "archive";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String archivePrefix;
  private final boolean compress;
  private final String encryptionKey;
  private final Integer maxFilesInArchive;
  private final Long maxBytesInArchive;

  @JsonCreator
  public ArchiveBackupDriver(
          @JsonProperty("archive-prefix") String archivePrefix,
          @JsonProperty("compress") boolean compress,
          @JsonProperty("encrypt-with") String encryptionKey,
          @JsonProperty("max-files") Integer maxFilesInArchive,
          @JsonProperty("max-size") @JsonDeserialize(using = SizeToBytesDeserializer.class) Long maxBytesInArchive) {
    this.archivePrefix = archivePrefix;
    this.compress = compress;
    this.encryptionKey = encryptionKey;
    this.maxFilesInArchive = maxFilesInArchive;
    this.maxBytesInArchive = maxBytesInArchive;
  }

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    char[] password = getPassword(encryptionKey, configuration, report);

    TarGzArchiver archiver = new TarGzArchiver(archivePrefix, compress, password, maxFilesInArchive, maxBytesInArchive);
    return new ArchiveBackupSession(dbClient, report, backupDirectories, archiver);
  }

  @Override
  public String getInformalName() {
    return INFORMAL_NAME;
  }

  @Override
  public VersioningDriver getVersioningDriver() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("This driver does not support versioning");
  }

  private class ArchiveBackupSession extends AbstractBackupSession {
    private final TarGzArchiver archiver;
    private final Map<UUID, ValuePair<String, String>> directoryPrefixes = new HashMap<>();

    ArchiveBackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, TarGzArchiver archiver) {
      super(dbClient, report, backupDirectories);
      this.archiver = archiver;
    }

    @Override
    protected void init() {
      super.init();
      for (BackupDirectory backupDirectory : backupDirectories) {
        if (backupDirectory.getConfiguration().getStoreAs() != null) {
          directoryPrefixes.put(backupDirectory.getId(), new ValuePair<>(backupDirectory.getConfiguration().getDirectory().toString(), backupDirectory.getConfiguration().getStoreAs()));
        }
      }
    }

    @Override
    protected void finish() {
      super.finish();
      try {
        archiver.close();
      } catch (IOException e) {
        logger.error("Could not close archiver properly", e);
        report.addWarning("Could not close archive '%s' properly, some data may be missing", archiver.getTargetFile());
      }
    }

    @Override
    protected void handleFile(BackupFile backupFile) {
      String entryName = backupFile.file.toString();

      if (directoryPrefixes.containsKey(backupFile.directoryId)) {
        ValuePair<String, String> stripAndPrefix = directoryPrefixes.get(backupFile.directoryId);
        String strip = stripAndPrefix.getLeft();
        String prefix = stripAndPrefix.getRight();
        if (!entryName.startsWith(strip)) {
          logger.error("File '{}' should start with prefix '{}'", backupFile.file, prefix);
          report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
          report.getTargetReportWriter().failedFile();
          return;
        }

        entryName = prefix + entryName.substring(strip.length());
      }

      try {
        if (backupFile.file.exists()) {
          archiver.addFile(backupFile.file, entryName);
        } else {
          archiver.deleteFile(backupFile.file, entryName, backupFile.lastModified);
        }
        report.getTargetReportWriter().successfulFile();
      } catch (IOException e) {
        logger.error("Could not add file '{}'", backupFile.file);
        logger.error("", e);
        report.addError("Could not add file '%s' to archive", backupFile.file);
        report.getTargetReportWriter().failedFile();
      }
    }
  }
}
