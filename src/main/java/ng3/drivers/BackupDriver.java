package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.conf.Configuration;
import ng3.db.DbClient;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "driver")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FileCopyBackupDriver.class, name = FileCopyBackupDriver.INFORMAL_NAME),
    @JsonSubTypes.Type(value = ArchiveBackupDriver.class, name = ArchiveBackupDriver.INFORMAL_NAME)
})
public interface BackupDriver {
  BackupSession startSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories);
  String getInformalName();

  interface BackupSession {
    void endSession();
  }
}
