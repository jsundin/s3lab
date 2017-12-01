package ng3.drivers.s3;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
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

import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class S3BackupDriver extends AbstractBackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  public static final String INFORMAL_NAME = "s3";
  private final String bucket;
  private final String region;
  private final String accessKeyRef;
  private final String secretKeyRef;
  private final int threads;
  private final boolean compress;
  private final String encryptionKey;

  @JsonCreator
  public S3BackupDriver(
      @JsonProperty("bucket") String bucket,
      @JsonProperty("region") String region,
      @JsonProperty("access-key") String accessKeyRef,
      @JsonProperty("secret-key") String secretKeyRef,
      @JsonProperty("threads") Integer threads,
      @JsonProperty("compress") boolean compress,
      @JsonProperty("encrypt-with") String encryptionKey) {
    this.bucket = bucket;
    this.region = region;
    this.accessKeyRef = accessKeyRef;
    this.secretKeyRef = secretKeyRef;
    this.threads = threads == null || threads < 2 ? 1 : threads;
    this.compress = compress;
    this.encryptionKey = encryptionKey;
  }

  @Override
  public void start(Configuration configuration) {
  }

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    char[] accessKey = getPassword(accessKeyRef, configuration, null);
    char[] secretKey = getPassword(secretKeyRef, configuration, null);

    AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration("http://localhost:9444/s3", region);
    AmazonS3 client = AmazonS3ClientBuilder
        .standard()
        .withEndpointConfiguration(endpoint)
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(new String(accessKey), new String(secretKey))))
        .disableChunkedEncoding()
        .withPathStyleAccessEnabled(true)
        .build();

    char[] password = getPassword(encryptionKey, configuration, report);

    return new S3BackupSession(dbClient, report, backupDirectories, client, password);
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
    private final AmazonS3 client;
    private final ExecutorService executor;
    private final Semaphore threadSemaphore;
    private final Map<UUID, ValuePair<String, String>> storeAs;
    private final char[] encryptionPassword;

    public S3BackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, AmazonS3 client, char[] encryptionPassword) {
      super(dbClient, report, backupDirectories);
      this.client = client;
      this.encryptionPassword = encryptionPassword;
      executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("s3"));
      threadSemaphore = new Semaphore(threads);

      storeAs = new HashMap<>();
      for (BackupDirectory backupDirectory : backupDirectories) {
        if (backupDirectory.getConfiguration().getStoreAs() != null) {
          storeAs.put(backupDirectory.getId(), new ValuePair<>(backupDirectory.getConfiguration().getDirectory().toString(), backupDirectory.getConfiguration().getStoreAs()));
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
          executor.awaitTermination(999, TimeUnit.DAYS); // TODO: ->Settings
          break;
        } catch (InterruptedException ignored) {
          Thread.interrupted();
        }
      }
    }

    @Override
    protected void handleFile(BackupFile backupFile) {
      logger.info("Handle file '{}'", backupFile.file);

      String target = backupFile.file.toString();
      if (storeAs.containsKey(backupFile.directoryId)) {
        ValuePair<String, String> prefixAndStoreAs = storeAs.get(backupFile.directoryId);
        if (!target.startsWith(prefixAndStoreAs.getLeft())) {
          logger.error("File '{}' should start with prefix '{}'", target, prefixAndStoreAs.getLeft());
          report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
          report.getTargetReportWriter().failedFile();
          return;
        }
        target = prefixAndStoreAs.getRight() + target.substring(prefixAndStoreAs.getLeft().length());
      } else {
        target = target.substring(1);
      }

      UploadFileTask uploadFileTask;
      if (encryptionPassword == null) {
        uploadFileTask = new UploadFileTask(client, bucket, backupFile, target, compress);
      } else {
        byte[] salt = CryptoUtils.generateSalt();
        Key key = CryptoUtils.generateKey(encryptionPassword, salt);
        uploadFileTask = new UploadFileTask(client, bucket, backupFile, target, compress, key, salt);
      }

      threadSemaphore.acquireUninterruptibly();
      executor.submit(() -> {
        try {
          uploadFileTask.execute();
          report.getTargetReportWriter().successfulFile();
          //uploadFinished(backupFile); // TODO: den här ska anropas
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
