package ng3.drivers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;
import ng3.BackupDirectory;
import ng3.Settings;
import ng3.agent.BackupReportWriter;
import ng3.common.CryptoUtils;
import ng3.common.SimpleThreadFactory;
import ng3.common.ValuePair;
import ng3.conf.Configuration;
import ng3.db.DbClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public class FileCopyBackupDriver extends AbstractBackupDriver implements VersionedBackupDriver {
  public static final String INFORMAL_NAME = "file-copy";
  private static final String COMPRESS_EXTENSION = ".gz";
  private static final String ENCRYPT_EXTENSION = ".crypt";
  private static final String DELETED_EXTENSION = ".deleted";
  private static final String META_EXTENSION = ".meta";

  private Logger logger = LoggerFactory.getLogger(getClass());
  private final File path;
  private final int threads;
  private final String encryptionKey;
  private final boolean compress;

  @JsonCreator
  public FileCopyBackupDriver(
          @JsonProperty("path") File path,
          @JsonProperty("threads") Integer threads,
          @JsonProperty("encrypt-with") String encryptionKey,
          @JsonProperty("compress") boolean compress) {
    this.path = path;
    this.threads = threads == null || threads < 2 ? 1 : threads;
    this.encryptionKey = encryptionKey;
    this.compress = compress;
  }

  @Override
  public String getInformalName() {
    return INFORMAL_NAME;
  }

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    char[] password = getPassword(encryptionKey, configuration, report);
    return new FileCopyBackupSession(dbClient, report, backupDirectories, password);
  }

  @Override
  public void performVersioning(DbClient dbClient, Configuration configuration, BackupDirectory backupDirectory) {
    File directory = backupDirectory.getConfiguration().getDirectory();
    String storeAs = backupDirectory.getConfiguration().getStoreAs();

    File target;
    if (storeAs == null) {
      target = new File(path, directory.toString());
    } else {
      target = new File(path, storeAs);
    }

    System.out.println(">> " + target);
  }

  public class FileCopyBackupSession extends AbstractBackupSession {
    private final ExecutorService executor;
    private final Semaphore threadSemaphore;
    private final Map<UUID, ValuePair<File, String>> backupTargets = new HashMap<>();
    private final char[] encryptionPassword;

    FileCopyBackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, char[] encryptionPassword) {
      super(dbClient, report, backupDirectories);
      this.encryptionPassword = encryptionPassword;
      executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopy"));
      threadSemaphore = new Semaphore(threads);
    }

    @Override
    protected void init() {
      super.init();
      for (BackupDirectory backupDirectory : backupDirectories) {
        UUID id = backupDirectory.getId();
        File directory = backupDirectory.getConfiguration().getDirectory();
        String storeAs = backupDirectory.getConfiguration().getStoreAs();

        if (storeAs == null) {
          backupTargets.put(id, new ValuePair<>(path, ""));
        } else {
          backupTargets.put(id, new ValuePair<>(new File(path, storeAs), directory.toString()));
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
          executor.awaitTermination(999, TimeUnit.DAYS);
          break;
        } catch (InterruptedException ignored) {
          Thread.interrupted();
        }
      }
    }

    @Override
    protected void handleFile(BackupFile backupFile) {
      ValuePair<File, String> targetAndPrefix = backupTargets.get(backupFile.directoryId);
      if (targetAndPrefix == null) {
        logger.error("Could not find directory prefix '{}' in prepared map when handling file '{}' - this shouldn't happen", backupFile.directoryId, backupFile.file);
        report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
        report.getTargetReportWriter().failedFile();
        return;
      }

      File target = targetAndPrefix.getLeft();
      String prefix = targetAndPrefix.getRight();
      String fqfn = backupFile.file.toString();
      if (!fqfn.startsWith(prefix)) {
        logger.error("File '{}' should start with prefix '{}'", fqfn, prefix);
        report.addError("Internal error while processing '%s' - see system logs for details", backupFile.file);
        report.getTargetReportWriter().failedFile();
        return;
      }
      fqfn = fqfn.substring(prefix.length());
      target = new File(target, fqfn);

      CopyFileTask copyFileTask;
      if (encryptionPassword == null) {
        copyFileTask = new CopyFileTask(backupFile.file, target);
      } else {
        byte[] salt = CryptoUtils.generateSalt();
        Key key = CryptoUtils.generateKey(encryptionPassword, salt);
        copyFileTask = new CopyFileTask(backupFile.file, target, key, salt);
      }

      threadSemaphore.acquireUninterruptibly();
      executor.submit(() -> {
        try {
          Boolean result = copyFileTask.execute();
          if (result) {
            report.getTargetReportWriter().successfulFile();
          } else {
            report.getTargetReportWriter().failedFile();
          }
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

  private class CopyFileTask {
    private final File src;
    private final File target;
    private final Key key;
    private final byte[] salt;

    private CopyFileTask(File src, File target) {
      this.src = src;
      this.target = target;
      key = null;
      salt = null;
    }

    private CopyFileTask(File src, File target, Key key, byte[] salt) {
      this.src = src;
      this.target = target;
      this.key = key;
      this.salt = salt;
    }

    private boolean execute() throws Exception {
      if (!src.exists()) {
        return delete();
      } else {
        return copy();
      }
    }

    private boolean delete() throws Exception {
      File target = FileTools.addExtension(getFile(this.target), DELETED_EXTENSION);
      try (FileOutputStream ignored = new FileOutputStream(target)) {
      }
      return true;
    }

    private boolean copy() throws Exception {
      DigestOutputStream fileOut = null;
      OutputStream lastOut = null;
      CipherOutputStream cipherOut = null;
      GZIPOutputStream gzOut = null;
      byte[] iv = null;

      File target = getFile(this.target);
      if (!target.getParentFile().exists()) {
        if (!target.getParentFile().mkdirs()) {
          logger.error("Could not create directory '{}'", target.getParentFile());
          return false;
        }
      }

      try (FileInputStream fileIn = new FileInputStream(src)) {
        lastOut = fileOut = new DigestOutputStream(new FileOutputStream(target));

        if (key != null && salt != null) {
          iv = CryptoUtils.generateIV();
          lastOut = cipherOut = CryptoUtils.getEncryptionOutputStream(key, iv, lastOut);
        }

        if (compress) {
          lastOut = gzOut = new GZIPOutputStream(lastOut);
        }

        IOUtils.copy(fileIn, lastOut);
      } finally {
        IOUtils.closeQuietly(gzOut, cipherOut, fileOut);
      }

      Metadata.FileMeta.Builder metaBuilder = Metadata.FileMeta.newBuilder()
              .setFormatVersion(1) // TODO: byt namn på proto-nyckel
              .setEncrypted(key != null)
              .setFileMD5(ByteString.copyFrom(fileOut.getDigest()));

      if (key != null && salt != null) {
        metaBuilder.setKeyIterations(Settings.KEY_ITERATIONS);
        metaBuilder.setKeyLength(Settings.KEY_LENGTH);
        metaBuilder.setKeyAlgorithm(Settings.KEY_ALGORITHM);
        metaBuilder.setCryptoAlgorithm(Settings.CIPHER_TRANSFORMATION); // TODO: byt namn på proto-nyckel
        metaBuilder.setSalt(ByteString.copyFrom(salt));
        metaBuilder.setIv(ByteString.copyFrom(iv));
      }
      Metadata.FileMeta meta = metaBuilder.build();

      File metaFile = FileTools.addExtension(target, META_EXTENSION);
      try (FileOutputStream metaOut = new FileOutputStream(metaFile)) {
        meta.writeTo(metaOut);
      }

      return true;
    }

    private File getFile(File src) {
      File file = src;
      if (compress) {
        file = FileTools.addExtension(file, COMPRESS_EXTENSION);
      }
      if (key != null && salt != null) {
        file = FileTools.addExtension(file, ENCRYPT_EXTENSION);
      }
      return getVersionedFile(file);
    }

    private File getVersionedFile(File src) {
      File test = src;
      int n = 1;
      while (test.exists() || FileTools.addExtension(test, DELETED_EXTENSION).exists()) {
        test = FileTools.addExtension(src, "," + n++);
      }
      return test;
    }
  }
}
