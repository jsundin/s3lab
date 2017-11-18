package ng3.drivers.s3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import ng3.drivers.AbstractBackupDriver;
import ng3.drivers.VersioningDriver;

import java.util.List;

public class S3BackupDriver extends AbstractBackupDriver {
  public static final String INFORMAL_NAME = "s3";
  private final String bucket;
  private final int threads;
  private final boolean compress;
  private final String encryptionKey;

  @JsonCreator
  public S3BackupDriver(
          @JsonProperty("bucket") String bucket,
          @JsonProperty("threads") Integer threads,
          @JsonProperty("compress") boolean compress,
          @JsonProperty("encrypt-with") String encryptionKey) {
    this.bucket = bucket;
    this.threads = threads == null || threads < 2 ? 1 : threads;
    this.compress = compress;
    this.encryptionKey = encryptionKey;
  }

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
