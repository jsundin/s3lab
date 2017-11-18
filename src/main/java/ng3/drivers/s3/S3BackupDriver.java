package ng3.drivers.s3;

import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import ng3.drivers.AbstractBackupDriver;
import ng3.drivers.VersioningDriver;

import java.util.List;

public class S3BackupDriver extends AbstractBackupDriver {
  public static final String INFORMAL_NAME = "s3";

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    return new S3BackupSession(dbClient, report, backupDirectories);
  }

  @Override
  public String getInformalName() {
    return INFORMAL_NAME;
  }

  @Override
  public VersioningDriver getVersioningDriver() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("This driver does not support versioning");
  }

  public class S3BackupSession extends AbstractBackupSession {

    public S3BackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
      super(dbClient, report, backupDirectories);
    }

    @Override
    protected void handleFile(BackupFile backupFile) {

    }
  }
}
