package ng3.drivers.filecopy;

import ng3.BackupDirectory;
import ng3.Metadata;
import ng3.agent.VersioningReportWriter;
import ng3.common.SimpleThreadFactory;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import ng3.drivers.VersioningDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author johdin
 * @since 2017-11-17
 */
public class FileCopyVersioningDriver implements VersioningDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final File path;
  private final int threads;

  public FileCopyVersioningDriver(File path, int threads) {
    this.path = path;
    this.threads = threads;
  }

  @Override
  public void performVersioning(DbClient dbClient, Configuration configuration, VersioningReportWriter report, List<BackupDirectory> backupDirectories) {
    ExecutorService executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopyVersioning"));
    for (BackupDirectory backupDirectory : backupDirectories) {
      executor.submit(() -> {
        try {
          File target;
          if (backupDirectory.getConfiguration().getStoreAs() == null) {
            target = new File(path, backupDirectory.getConfiguration().getDirectory().toString());
          } else {
            target = new File(path, backupDirectory.getConfiguration().getStoreAs());
          }

          performVersioning(target, backupDirectory, report);
        } catch (Throwable error) {
          logger.error("Unhandled error", error);
        }
      });
    }
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

  private void performVersioning(File directory, BackupDirectory backupDirectory, VersioningReportWriter report) {
    File[] files = directory.listFiles();
    if (files == null) {
      logger.warn("Could not access directory '{}'", directory);
      return;
    }

    for (File file : files) {
      if (file.isDirectory() && file.getName().startsWith(FileCopyBackupDriver.FILE_PREFIX)) {
        performFileVersioning(file, backupDirectory, report);
        cleanup(file, report);
      } else if (file.isDirectory()) {
        performVersioning(file, backupDirectory, report);
        cleanup(file, report);
      } else {
        report.addWarning("Unexpected file '{}' - ignored", file);
        logger.warn("Unexpected file '{}' - ignored", file);
      }
    }
  }

  private void cleanup(File dir, VersioningReportWriter report) {
    File[] files = dir.listFiles();
    if (files != null && files.length == 0) {
      if (!dir.delete()) {
        report.addWarning("Could not remove empty directory '{}'", dir);
        logger.warn("Could not remove empty directory '{}'", dir);
      } else {
        report.removedEmptyDirectory();
      }
    }
  }

  private void performFileVersioning(File dir, BackupDirectory backupDirectory, VersioningReportWriter report) {
    File[] files = dir.listFiles();
    if (files == null) {
      report.addWarning("Could not access file versions in directory '{}'", dir);
      logger.warn("Could not access file versions in directory '{}'", dir);
      return;
    }

    if (files.length == 0) {
      return;
    }

    Pattern versionFilePattern = Pattern.compile("^(?<version>[0-9]+)$");
    Pattern metaFilePattern = Pattern.compile("^([0-9]+\\.meta)$");
    Map<Integer, Metadata.Meta> versionMeta = new HashMap<>();

    for (File file : files) {
      if (!file.isFile()) {
        report.addError("File '{}' in '{}' should be a file, but it isn't", file, dir);
        logger.warn("File '{}' is not a file in file-directory", file);
        return;
      }

      String filename = file.getName();

      Matcher versionMatcher = versionFilePattern.matcher(filename);
      if (!metaFilePattern.matcher(filename).matches() && !versionMatcher.matches()) {
        report.addError("Unknown file '{}' in directory '{}'", file, dir);
        logger.warn("Unknown file '{}' in file-directory", file);
        return;
      }

      if (!versionMatcher.matches()) {
        // this is a meta file - we ignore those here
        continue;
      }

      int version = Integer.parseInt(versionMatcher.group("version"));
      File metaFile = new File(file.getParent(), String.format("%d%s", version, FileCopyBackupDriver.META_EXTENSION));

      Metadata.Meta meta;
      try (FileInputStream metaIn = new FileInputStream(metaFile)) {
        meta = Metadata.Meta.parseFrom(metaIn);
      } catch (IOException e) {
        report.addError("Could not read metadata for version '{}' in directory '{}'", version, dir);
        logger.warn("", e);
        return;
      }

      if (versionMeta.containsKey(version)) {
        report.addError("Version '{}' exists more than once in directory '{}'", version, dir);
        logger.warn("Version '{}' exists more than once with file '{}'", version, file);
        return;
      }
      versionMeta.put(version, meta);
    }

    ArrayList<Integer> versions = new ArrayList<>(versionMeta.keySet());
    Collections.sort(versions);
    Collections.reverse(versions);

    List<Integer> versionsToDelete = null;

    if (versionMeta.get(versions.get(0)).getDeleted()) {
      if (backupDirectory.getConfiguration().getDeletedFileVersioning() != null) {
        versionsToDelete = backupDirectory.getConfiguration().getDeletedFileVersioning().performVersioning(versionMeta);
      }
    } else {
      if (backupDirectory.getConfiguration().getFileVersioning() != null) {
        versionsToDelete = backupDirectory.getConfiguration().getFileVersioning().performVersioning(versionMeta);
      }
    }

    if (versionsToDelete != null) {
      for (Integer version : versionsToDelete) {
        File versionFile = new File(dir, String.format("%d", version));
        File metaFile = new File(dir, String.format("%d%s", version, FileCopyBackupDriver.META_EXTENSION));
        boolean deleted = false;
        boolean metaDeleted = false;
        if (versionFile.exists()) {
          deleted = versionFile.delete();
          if (!deleted) {
            report.addError("Could not delete version '{}' in directory '{}'", version, dir);
            logger.error("Could not delete version file '{}'", versionFile);
            continue;
          }
        }
        if (deleted && metaFile.exists()) {
          metaDeleted = metaFile.delete();
          if (!metaDeleted) {
            report.addError("Could not delete metadata for version '{}' in directory '{}'", version, dir);
            logger.error("Could not delete version metafile '{}'", metaFile);
          }
        }

        if (deleted && metaDeleted) {
          report.removedVersion();
        }
      }
    }
  }
}
