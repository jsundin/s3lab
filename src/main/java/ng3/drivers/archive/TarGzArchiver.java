package ng3.drivers.archive;

import com.google.protobuf.ByteString;
import ng3.Settings;
import ng3.common.CryptoUtils;
import ng3.common.TimeUtilsNG;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.DigestOutputStream;
import s4lab.FileTools;
import s4lab.agent.SecurityException;

import javax.crypto.CipherOutputStream;
import java.io.*;
import java.nio.file.Files;
import java.security.Key;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class TarGzArchiver {
  private static final String DELETE_MARKER = "#DELETED#";
  private final static String TIMESTAMP_FORMAT = "yMD-Hms";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String archivePrefix;
  private final boolean compress;
  private final char[] password;
  private final Integer maxFilesInArchive;
  private final Long maxBytesInArchive;
  private DigestOutputStream digestOutputStream;
  private CipherOutputStream cipherOutputStream;
  private GZIPOutputStream gzipOutputStream;
  private TarArchiveOutputStream tarOutputStream;
  private OutputStream outputStream;
  private int filesInArchive;
  private long bytesInArchive;
  private int archiveIndex = 1;
  private final String timestamp;
  private ng3.Metadata.Meta.Builder metaBuilder;
  private File targetFile;

  public TarGzArchiver(String archivePrefix, boolean compress, char[] password, Integer maxFilesInArchive, Long maxBytesInArchive) {
    this.archivePrefix = archivePrefix;
    this.compress = compress;
    this.password = password;
    this.maxFilesInArchive = maxFilesInArchive;
    this.maxBytesInArchive = maxBytesInArchive;
    timestamp = new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date());
  }

  public void close() throws IOException {
    closeArchive();
  }

  public void deleteFile(File file, String name, ZonedDateTime deletionTime) throws IOException {
    TarArchiveEntry entry = getArchiveEntry(file, name);
    entry.setUserName(DELETE_MARKER);
    entry.setSize(0);
    entry.setModTime(TimeUtilsNG.at(deletionTime).toDate());
    tarOutputStream.putArchiveEntry(entry);
    tarOutputStream.closeArchiveEntry();
    filesInArchive++;
  }

  public void addFile(File file, String name) throws IOException {
    TarArchiveEntry entry = getArchiveEntry(file, name);
    try {
      Map<String, Object> attributes = Files.readAttributes(file.toPath(), "unix:uid,gid,mode");
      entry.setIds((int) attributes.get("uid"), (int) attributes.get("gid"));
      entry.setMode((int) attributes.get("mode"));
    } catch (IllegalArgumentException ignored) {}
    entry.setSize(file.length());
    entry.setModTime(file.lastModified());

    tarOutputStream.putArchiveEntry(entry);
    try (FileInputStream fis = new FileInputStream(file)) {
      long len = IOUtils.copy(fis, tarOutputStream);
      bytesInArchive += len;
    }
    tarOutputStream.closeArchiveEntry();
    filesInArchive++;
  }

  public File getTargetFile() {
    return targetFile;
  }

  private File buildTargetFile() {
    String fname = String.format("%s%s-%04d.tar%s%s", archivePrefix, timestamp, archiveIndex, compress ? ".gz" : "", password != null ? ".crypt" : "");
    return new File(fname);
  }

  private void cycleArchive() throws IOException, SecurityException {
    if (outputStream != null && ((maxFilesInArchive != null && filesInArchive >= maxFilesInArchive) || (maxBytesInArchive != null && bytesInArchive >= maxBytesInArchive))) {
      closeArchive();
      archiveIndex++;
      filesInArchive = 0;
      bytesInArchive = 0;
    }

    if (outputStream == null) {
      metaBuilder = ng3.Metadata.Meta.newBuilder()
              .setEncrypted(password != null);

      targetFile = buildTargetFile();
      outputStream = digestOutputStream = new DigestOutputStream(new FileOutputStream(targetFile));
      if (password != null) {
        byte[] salt = CryptoUtils.generateSalt();
        byte[] iv = CryptoUtils.generateIV();
        Key key = CryptoUtils.generateKey(password, salt);
        outputStream = cipherOutputStream = CryptoUtils.getEncryptionOutputStream(key, iv, outputStream);

        metaBuilder.setKeyAlgorithm(Settings.KEY_ALGORITHM)
                .setKeyIterations(Settings.KEY_ITERATIONS)
                .setKeyLength(Settings.KEY_LENGTH)
                .setCipherTransformation(Settings.CIPHER_TRANSFORMATION)
                .setIv(ByteString.copyFrom(iv))
                .setSalt(ByteString.copyFrom(salt));
      }
      if (compress) {
        outputStream = gzipOutputStream = new GZIPOutputStream(outputStream);
      }
      outputStream = tarOutputStream = new TarArchiveOutputStream(outputStream);
    }
  }

  private void closeArchive() throws IOException {
    if (outputStream != null) {
      IOUtils.closeQuietly(tarOutputStream);
      IOUtils.closeQuietly(gzipOutputStream);
      IOUtils.closeQuietly(cipherOutputStream);
      IOUtils.closeQuietly(digestOutputStream);

      metaBuilder.setFileMD5(ByteString.copyFrom(digestOutputStream.getDigest()));
      ng3.Metadata.Meta meta = metaBuilder.build();
      try (FileOutputStream fos = new FileOutputStream(FileTools.addExtension(targetFile, ".meta"))) {
        meta.writeTo(fos);
      } finally {
        digestOutputStream = null;
        cipherOutputStream = null;
        gzipOutputStream = null;
        tarOutputStream = null;
        outputStream = null;
      }
    }
  }

  private TarArchiveEntry getArchiveEntry(File file, String name) throws IOException {
    try {
      cycleArchive();
    } catch (SecurityException e) {
      throw new IOException(e);
    }

    return new TarArchiveEntry(file, name);
  }
}
