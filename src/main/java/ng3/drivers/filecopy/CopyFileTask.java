package ng3.drivers.filecopy;

import com.google.protobuf.ByteString;
import ng3.Metadata;
import ng3.Settings;
import ng3.common.CryptoUtils;
import ng3.common.TimeUtilsNG;
import ng3.drivers.AbstractBackupDriver;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.DigestOutputStream;
import s4lab.FileTools;

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
 * @since 2017-11-17
 */
class CopyFileTask {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AbstractBackupDriver.BackupFile backupFile;
  private final File target;
  private final boolean compress;
  private final Key key;
  private final byte[] salt;

  CopyFileTask(AbstractBackupDriver.BackupFile backupFile, File target, boolean compress) {
    this(backupFile, target, compress, null, null);
  }

  CopyFileTask(AbstractBackupDriver.BackupFile backupFile, File target, boolean compress, Key key, byte[] salt) {
    this.backupFile = backupFile;
    this.target = target;
    this.compress = compress;
    this.key = key;
    this.salt = salt;
  }

  boolean execute() throws Exception {
    return backupFile.deleted ? delete() : copy();
  }

  private boolean copy() throws Exception {
    if (!target.exists()) {
      if (!target.mkdirs()) {
        logger.error("Could not create directories for '{}' (target '{}')", backupFile.file, target);
        return false;
      }
    }

    if (!target.isDirectory()) {
      logger.error("Target '{}' is not a directory", target);
      return false;
    }

    File targetFile = getVersionedFile();
    OutputStream out;
    DigestOutputStream digestOut = null;
    CipherOutputStream cipherOut = null;
    GZIPOutputStream gzOut = null;
    byte[] iv = null;

    try (FileInputStream in = new FileInputStream(backupFile.file)) {
      out = digestOut = new DigestOutputStream(new FileOutputStream(targetFile));

      if (key != null && salt != null) {
        iv = CryptoUtils.generateIV();
        out = cipherOut = CryptoUtils.getEncryptionOutputStream(key, iv, out);
      }

      if (compress) {
        out = gzOut = new GZIPOutputStream(out);
      }

      IOUtils.copy(in, out);
    } finally {
      IOUtils.closeQuietly(gzOut, cipherOut, digestOut);
    }

    Metadata.Meta.Builder metaBuilder = ng3.Metadata.Meta.newBuilder()
            .setLastModified(TimeUtilsNG.at(backupFile.lastModified).to(ZoneOffset.UTC).toISOString())
            .setFileMD5(ByteString.copyFrom(digestOut.getDigest()));

    try {
      Map<String, Object> attributes = Files.readAttributes(backupFile.file.toPath(), "unix:uid,gid,mode");
      metaBuilder.setUid((int) attributes.get("uid"))
              .setGid((int) attributes.get("gid"))
              .setMode((int) attributes.get("mode"));
    } catch (IllegalArgumentException ignored) {}

    if (key != null && salt != null) {
      metaBuilder.setEncrypted(true)
              .setKeyAlgorithm(Settings.KEY_ALGORITHM)
              .setKeyIterations(Settings.KEY_ITERATIONS)
              .setKeyLength(Settings.KEY_LENGTH)
              .setCipherTransformation(Settings.CIPHER_TRANSFORMATION)
              .setIv(ByteString.copyFrom(iv))
              .setSalt(ByteString.copyFrom(salt));
    }

    ng3.Metadata.Meta meta = metaBuilder.build();
    File metaFile = FileTools.addExtension(targetFile, FileCopyBackupDriver.META_EXTENSION);
    try (FileOutputStream metaOut = new FileOutputStream(metaFile)) {
      meta.writeTo(metaOut);
    }

    return true;
  }

  private boolean delete() throws Exception {
    if (!target.exists()) {
      return true; // file never existed, we don't need to mark it as deleted
    }

    if (!target.isDirectory()) {
      logger.error("Target '{}' is not a directory", target);
    }

    File targetFile = getVersionedFile();
    try (FileOutputStream ignored = new FileOutputStream(targetFile)) {
    }

    Metadata.Meta meta = Metadata.Meta.newBuilder()
            .setDeleted(true)
            .setLastModified(TimeUtilsNG.at(backupFile.lastModified).to(ZoneOffset.UTC).toISOString())
            .build();

    File metaFile = FileTools.addExtension(targetFile, FileCopyBackupDriver.META_EXTENSION);
    try (FileOutputStream metaOut = new FileOutputStream(metaFile)) {
      meta.writeTo(metaOut);
    }
    return true;
  }

  private File getVersionedFile() {
    int n = 1;
    File f;
    do {
      f = new File(target, "" + n++);
    } while (f.exists());
    return f;
  }
}
