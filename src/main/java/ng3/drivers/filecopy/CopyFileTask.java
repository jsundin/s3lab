package ng3.drivers.filecopy;

import com.google.protobuf.ByteString;
import ng3.Settings;
import ng3.common.CryptoUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.DigestOutputStream;
import s4lab.FileTools;
import s4lab.agent.Metadata;

import javax.crypto.CipherOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.Key;
import java.util.zip.GZIPOutputStream;

/**
 * @author johdin
 * @since 2017-11-17
 */
class CopyFileTask {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File src;
  private final File target;
  private final boolean compress;
  private final Key key;
  private final byte[] salt;

  CopyFileTask(File src, File target, boolean compress) {
    this(src, target, compress, null, null);
  }

  CopyFileTask(File src, File target, boolean compress, Key key, byte[] salt) {
    this.src = src;
    this.target = target;
    this.compress = compress;
    this.key = key;
    this.salt = salt;
  }

  boolean execute() throws Exception {
    return src.exists() ? copy() : delete();
  }

  private boolean copy() throws Exception {
    if (!target.exists()) {
      if (!target.mkdirs()) {
        logger.error("Could not create directories for '{}' (target '{}')", src, target);
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

    try (FileInputStream in = new FileInputStream(src)) {
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

    Metadata.FileMeta.Builder metaBuilder = Metadata.FileMeta.newBuilder()
        .setFormatVersion(1) // TODO: bort med nyckel!
        .setEncrypted(key != null)
        .setFileMD5(ByteString.copyFrom(digestOut.getDigest()));

    if (key != null && salt != null) {
      metaBuilder.setKeyIterations(Settings.KEY_ITERATIONS);
      metaBuilder.setKeyLength(Settings.KEY_LENGTH);
      metaBuilder.setKeyAlgorithm(Settings.KEY_ALGORITHM);
      metaBuilder.setCryptoAlgorithm(Settings.CIPHER_TRANSFORMATION); // TODO: byt namn på proto-nyckel
      metaBuilder.setSalt(ByteString.copyFrom(salt));
      metaBuilder.setIv(ByteString.copyFrom(iv));
    }
    Metadata.FileMeta meta = metaBuilder.build();
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
    try (FileOutputStream ignored = new FileOutputStream(FileTools.addExtension(targetFile, FileCopyBackupDriver.DELETED_EXTENSION))) {
    }
    return true;
  }

  private File getVersionedFile() {
    int n = 1;
    File f;
    do {
      f = new File(target, "" + n++);
    } while (f.exists() || FileTools.addExtension(f, FileCopyBackupDriver.DELETED_EXTENSION).exists());
    return f;
  }
}
