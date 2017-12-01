package ng3.drivers.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.Base64;
import com.google.protobuf.ByteString;
import ng3.Metadata;
import ng3.Settings;
import ng3.common.CryptoUtils;
import ng3.common.TimeUtilsNG;
import ng3.drivers.AbstractBackupDriver;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.DigestInputStream;

import javax.crypto.CipherOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.Key;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * @author johdin
 * @since 2017-12-01
 */
class UploadFileTask {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AmazonS3 client;
  private final String bucket;
  private final AbstractBackupDriver.BackupFile backupFile;
  private final String target;
  private final boolean compress;
  private final Key key;
  private final byte[] salt;

  UploadFileTask(AmazonS3 client, String bucket, AbstractBackupDriver.BackupFile backupFile, String target, boolean compress) {
    this(client, bucket, backupFile, target, compress, null, null);
  }

  UploadFileTask(AmazonS3 client, String bucket, AbstractBackupDriver.BackupFile backupFile, String target, boolean compress, Key key, byte[] salt) {
    this.client = client;
    this.bucket = bucket;
    this.backupFile = backupFile;
    this.target = target;
    this.compress = compress;
    this.key = key;
    this.salt = salt;
  }

  void execute() throws Exception {
    if (backupFile.deleted) {
      delete();
    } else {
      upload();
    }
  }

  private void upload() throws Exception {
    Metadata.Meta.Builder metaBuilder = Metadata.Meta.newBuilder()
        .setLastModified(TimeUtilsNG.at(backupFile.lastModified).to(ZoneOffset.UTC).toISOString());

    boolean hasUnixDetails;
    try {
      Map<String, Object> attributes = Files.readAttributes(backupFile.file.toPath(), "unix:uid,gid,mode");
      metaBuilder.setUid((int) attributes.get("uid"))
          .setGid((int) attributes.get("gid"))
          .setMode((int) attributes.get("mode"));
      hasUnixDetails = true;
    } catch (IllegalArgumentException ignored) {
      hasUnixDetails = false;
    }

    File sourceFile;
    boolean deleteSourceFile = false;

    if (compress || (key != null && salt != null)) {
      sourceFile = createTempFile(metaBuilder);
      deleteSourceFile = true;
    } else {
      sourceFile = backupFile.file;
    }

    Metadata.Meta meta = metaBuilder.build();
    try (FileInputStream fis = new FileInputStream(sourceFile)) {
      client.putObject(bucket, target, fis, getObjectMetadata(meta, hasUnixDetails, sourceFile.length()));
    }

    if (deleteSourceFile) {
      if (!sourceFile.delete()) {
        logger.warn("Could not remove temporary file '{}'", sourceFile);
      }
    }
  }

  private void delete() {
    client.deleteObject(bucket, target);
  }

  private File createTempFile(Metadata.Meta.Builder metaBuilder) throws Exception {
    File tempFile = File.createTempFile("s3upload-", "", new File("/tmp")); // TODO: confa!
    DigestInputStream in = null;
    FileOutputStream fileOut = null;
    GZIPOutputStream gzOut = null;
    CipherOutputStream cipherOut = null;

    try {
      in = new DigestInputStream(new FileInputStream(backupFile.file));
      fileOut = new FileOutputStream(tempFile);
      OutputStream out = fileOut;

      if (compress) {
        out = gzOut = new GZIPOutputStream(out);
      }

      if (key != null && salt != null) {
        byte[] iv = CryptoUtils.generateIV();
        metaBuilder
            .setEncrypted(true)
            .setIv(ByteString.copyFrom(iv))
            .setKeyIterations(Settings.KEY_ITERATIONS)
            .setKeyLength(Settings.KEY_LENGTH)
            .setSalt(ByteString.copyFrom(salt))
            .setCipherTransformation(Settings.CIPHER_TRANSFORMATION)
            .setKeyAlgorithm(Settings.KEY_ALGORITHM);

        out = cipherOut = CryptoUtils.getEncryptionOutputStream(key, iv, out);
      }

      IOUtils.copy(in, out);
    } finally {
      IOUtils.closeQuietly(cipherOut, gzOut, fileOut, in);
    }
    metaBuilder.setFileMD5(ByteString.copyFrom(in.getDigest()));
    return tempFile;
  }

  private ObjectMetadata getObjectMetadata(Metadata.Meta meta, boolean hasUnixDetails, Long contentLength) {
    ObjectMetadata metadata = new ObjectMetadata();
    if (contentLength != null) {
      metadata.setContentLength(contentLength);
    }

    if (meta.getLastModified() != null) {
      metadata.addUserMetadata("last-modified", meta.getLastModified());
    }

    if (meta.getEncrypted()) {
      metadata.addUserMetadata("encrypted", meta.getEncrypted() ? "true" : "false");
      metadata.addUserMetadata("key-iterations", Integer.toString(meta.getKeyIterations()));
      metadata.addUserMetadata("key-length", Integer.toString(meta.getKeyLength()));
      metadata.addUserMetadata("cipher-transformation", meta.getCipherTransformation());
      metadata.addUserMetadata("key-algorithm", meta.getKeyAlgorithm());
      metadata.addUserMetadata("salt", Base64.encodeAsString(meta.getSalt().toByteArray()));
      metadata.addUserMetadata("iv", Base64.encodeAsString(meta.getIv().toByteArray()));
    }

    if (hasUnixDetails) {
      metadata.addUserMetadata("uid", Integer.toString(meta.getUid()));
      metadata.addUserMetadata("gid", Integer.toString(meta.getGid()));
      metadata.addUserMetadata("mode", Integer.toString(meta.getMode()));
    }

    if (meta.getDeleted()) {
      metadata.addUserMetadata("deleted", meta.getDeleted() ? "true" : "false");
    }

    if (meta.getFileMD5() != null) {
      metadata.addUserMetadata("file-md5", Base64.encodeAsString(meta.getFileMD5().toByteArray()));
    }
    return metadata;
  }
}
