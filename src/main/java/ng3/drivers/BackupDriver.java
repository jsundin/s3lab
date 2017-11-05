package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.db.DbClient;

import java.util.List;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "driver")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileCopyBackupDriver.class, name = "file-copy")
})
public interface BackupDriver {
  BackupSession startSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, UUID planId);

  interface BackupSession {
    void finish();
  }
}
