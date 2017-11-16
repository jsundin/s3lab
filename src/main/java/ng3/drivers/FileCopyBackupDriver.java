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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public class FileCopyBackupDriver extends AbstractBackupDriver implements VersionedBackupDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  public static final String INFORMAL_NAME = "file-copy";
  private final static String FILE_PREFIX = "$";
  private final static String META_EXTENSION = ".meta";
  private final static String DELETED_EXTENSION = ",DELETED";
  private final File path;
  private final int threads;
  private final boolean compress;
  private final String encryptionKey;

  @JsonCreator
  public FileCopyBackupDriver(
      @JsonProperty("path") File path,
      @JsonProperty("threads") Integer threads,
      @JsonProperty("compress") boolean compress,
      @JsonProperty("encrypt-with") String encryptionKey) {
    this.path = path;
    this.threads = threads == null || threads < 2 ? 1 : threads;
    this.compress = compress;
    this.encryptionKey = encryptionKey;
  }

  @Override
  public String getInformalName() {
    return INFORMAL_NAME;
  }

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    char[] password = getPassword(encryptionKey, configuration, report);
    return new BackupSession(dbClient, report, backupDirectories, threads, password);
  }

  @Override
  public void performVersioning(DbClient dbClient, Configuration configuration, BackupDirectory backupDirectory) {
    File target;
    if (backupDirectory.getConfiguration().getStoreAs() == null) {
      target = new File(path, backupDirectory.getConfiguration().getDirectory().toString());
    } else {
      target = new File(path, backupDirectory.getConfiguration().getStoreAs());
    }
    performDirectoryVersioning(target);
  }

  private void performDirectoryVersioning(File directory) {
    File[] files = directory.listFiles();
    if (files == null) {
      logger.warn("Could not access directory '{}'", directory);
      return;
    }

    // TODO: om katalogen är tom så kan vi ta bort den
    for (File file : files) {
      if (file.isDirectory()) {
        if (!file.getName().startsWith(FILE_PREFIX)) {
          performDirectoryVersioning(file);
        } else {
          performFileVersioning(file);
        }
      } else {
        logger.warn("Unexpected file '{}' - don't really know what to do with this -- ignored", file);
      }
    }
  }

  private void performFileVersioning(File fileDirectory) {
    // TODO: om filkatalogen är tom så kan vi ta bort den
    File[] fileVersions = fileDirectory.listFiles();
    if (fileVersions == null) {
      logger.warn("Could not access file-directory '{}'", fileDirectory);
      return;
    }

    Set<Integer> uniqueVersions = new HashSet<>();
    for (File fileVersion : fileVersions) {
      Pattern pattern = Pattern.compile("^(?<version>[0-9]+).*");
      Matcher m = pattern.matcher(fileVersion.getName());
      if (!m.matches()) {
        logger.warn("No version information in file '{}'", fileVersion);
        continue;
      }

      int version = Integer.parseInt(m.group("version"));
      uniqueVersions.add(version);
    }

    List<Integer> versions = new ArrayList<>(uniqueVersions);
    Collections.sort(versions);
    System.out.println(fileDirectory + ": " + versions);
  }

  public class BackupSession extends AbstractBackupDriver.AbstractBackupSession {
    private final ExecutorService executor;
    private final Semaphore threadSemaphore;
    private final Map<UUID, ValuePair<File, String>> backupTargets;
    private final char[] encryptionPassword;
    private final int threads;

    BackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories, int threads, char[] encryptionPassword) {
      super(dbClient, report, backupDirectories);
      executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopy"));
      threadSemaphore = new Semaphore(threads);
      this.threads = threads;
      this.encryptionPassword = encryptionPassword;

      backupTargets = new HashMap<>();
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
          executor.awaitTermination(999, TimeUnit.DAYS); // TODO: -> Settings
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
      target = new File(target.getParent(), FILE_PREFIX + target.getName());

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
          if (copyFileTask.execute()) {
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

    public CopyFileTask(File src, File target) {
      this.src = src;
      this.target = target;
      this.key = null;
      this.salt = null;
    }

    private CopyFileTask(File src, File target, Key key, byte[] salt) {
      this.src = src;
      this.target = target;
      this.key = key;
      this.salt = salt;
    }

    private boolean execute() throws Exception {
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
      OutputStream out = null;
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
      File metaFile = FileTools.addExtension(targetFile, META_EXTENSION);
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
      try (FileOutputStream out = new FileOutputStream(FileTools.addExtension(targetFile, DELETED_EXTENSION))) {

      }
      return true;
    }

    private File getVersionedFile() {
      int n = 1;
      File f;
      do {
        f = new File(target, "" + n++);
      } while (f.exists() || FileTools.addExtension(f, DELETED_EXTENSION).exists());
      return f;
    }
  }
}
