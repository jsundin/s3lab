package s4lab.agent.backuptarget.s3target;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.agent.FileUploadJob;
import s4lab.agent.backuptarget.BackupSession;
import s4lab.agent.backuptarget.BackupTarget;

import java.io.IOException;

public class S3BackupTarget implements BackupTarget {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String region;
  private final String bucketName;

  public S3BackupTarget(String region, String bucketName) {
    this.region = region;
    this.bucketName = bucketName;
  }

  @Override
  public BackupSession openSession() {
    AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new ProfileCredentialsProvider())
            .withRegion(region)
            .build();
    return new S3BackupSession(s3);
  }

  @Override
  public void closeSession(BackupSession session) {
    if (session == null || !(session instanceof S3BackupSession)) {
      throw new IllegalStateException("Invalid session type: " + (session == null ? "null" : session.getClass()));
    }

    ((S3BackupSession) session).close();
  }

  @Override
  public void handleJob(BackupSession session, FileUploadJob job) throws IOException {
    if (session == null || !(session instanceof S3BackupSession)) {
      throw new IllegalStateException("Invalid session type: " + (session == null ? "null" : session.getClass()));
    }

    ((S3BackupSession) session).handleJob(job);
  }

  public class S3BackupSession implements BackupSession {
    private final AmazonS3 s3;
    private boolean open = true;

    public S3BackupSession(AmazonS3 s3) {
      this.s3 = s3;
    }

    public void close() {
      open = false;
    }

    public void handleJob(FileUploadJob job) {
      logger.info("UPLOAD: " + job.getFile());

      /*Holder<byte[]> salt = new Holder<>();
      Holder<byte[]> iv = new Holder<>();

      try {
        Cipher cipher = SecurityManager.getInstance().getEncryptionCipher(salt, iv);

        FileInputStream fileIn = new FileInputStream(job.getFile());
        InputStream gzIn = FileTools.gzipInputStream(fileIn);
        CipherInputStream cipherIn = new CipherInputStream(gzIn, cipher);
        DigestInputStream digestIn = new DigestInputStream(cipherIn);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata("iv", DatatypeConverter.printHexBinary(iv.value));
        metadata.addUserMetadata("keyIterations", Integer.toString(SecurityManager.KEY_ITERATIONS));
        metadata.addUserMetadata("keyLength", Integer.toString(SecurityManager.KEY_LENGTH));
        metadata.addUserMetadata("salt", DatatypeConverter.printHexBinary(salt.value));
        metadata.addUserMetadata("cryptoAlgorithm", SecurityManager.ENCRYPTION_ALGORITHM);
        metadata.addUserMetadata("keyAlgorithm", SecurityManager.KEY_ALGORITHM);
        PutObjectResult result = s3.putObject(bucketName, job.getFile().toString(), digestIn, metadata);

        cipherIn.close();
        gzIn.close();
        fileIn.close();

        System.out.println("digest: " + DatatypeConverter.printHexBinary(digestIn.getDigest()));
        System.out.println("result.etag: " + result.getETag());
      } catch (Exception e) {
        e.printStackTrace();
      }*/
    }
  }
}
