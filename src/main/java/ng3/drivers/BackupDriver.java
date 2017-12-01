package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import ng3.drivers.archive.ArchiveBackupDriver;
import ng3.drivers.filecopy.FileCopyBackupDriver;
import ng3.drivers.s3.S3BackupDriver;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "driver")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FileCopyBackupDriver.class, name = FileCopyBackupDriver.INFORMAL_NAME),
    @JsonSubTypes.Type(value = ArchiveBackupDriver.class, name = ArchiveBackupDriver.INFORMAL_NAME),
    @JsonSubTypes.Type(value = S3BackupDriver.class, name = S3BackupDriver.INFORMAL_NAME)
})
public interface BackupDriver {
  void start(Configuration configuration);
  void finish();
  BackupSession startSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories);
  String getInformalName();
  VersioningDriver getVersioningDriver() throws UnsupportedOperationException;

  interface BackupSession {
    void endSession();
  }
}
