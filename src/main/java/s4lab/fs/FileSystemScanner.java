package s4lab.fs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.BackupAgent;
import s4lab.fs.rules.ExcludeRule;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileSystemScanner {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private int[] scan(BackupAgent backupAgent, File directory, FileFilter fileFilter) {
    File[] files = directory.listFiles();
    int[] foundFiles = {0, 0};
    if (files == null) {
      logger.warn("Cannot find any files  in '" + directory + "'");
      return foundFiles;
    }

    for (File file : files) {
      foundFiles[0]++;
      if (!fileFilter.accept(file)) {
        continue;
      }

      if (file.isDirectory()) {
        int[] subFoundFiles = scan(backupAgent, file, fileFilter);
        foundFiles[0] += subFoundFiles[0];
        foundFiles[1] += subFoundFiles[1];
      } else if (file.isFile()) {
        backupAgent.fileScanned(file);
        foundFiles[1]++;
      } else {
        logger.warn("Unknown file type: " + file);
      }
    }

    return foundFiles;
  }

  public void scan(BackupAgent backupAgent, List<DirectoryConfiguration> directoryConfigurations, ExcludeRule... globalExcludeRules) {
    logger.info("Started scanning " + directoryConfigurations.size() + " configurations");
    backupAgent.fileScanStarted();
    long t0 = System.currentTimeMillis();
    int[] foundFiles = {0, 0};

    for (DirectoryConfiguration directoryConfiguration : directoryConfigurations) {
      File directory = new File(directoryConfiguration.getDirectory());
      if (!directory.exists()) {
        logger.warn("Configured directory does not exist: " + directory); // TODO: exception?
        continue;
      }
      if (!directory.isDirectory()) {
        logger.warn("Configured directory is not a directory: " + directory); // TODO: exception?
        continue;
      }
      try {
        if (FileUtils.isSymlink(directory)) {
          logger.warn("Configured directory is a symlink: " + directory); // TODO: exception?
          continue;
        }
      } catch (IOException ignored) {}

      ExcludeRule[] mergedRules = (ExcludeRule[]) ArrayUtils.addAll(directoryConfiguration.getExcludeRules(), globalExcludeRules);
      int[] subFoundFiles = scan(backupAgent, directory, new ExcludingFileFilter(mergedRules));
      foundFiles[0] += subFoundFiles[0];
      foundFiles[1] += subFoundFiles[1];
    }

    logger.info("Finished scanning {} configurations, tagged {} of {} files in {}ms", directoryConfigurations.size(), foundFiles[1], foundFiles[0], System.currentTimeMillis() - t0);
    backupAgent.fileScanEnded();
  }

  private interface FileFilter {
    boolean accept(File file);
  }

  private class ExcludingFileFilter implements FileFilter {
    private final ExcludeRule[] excludeRules;

    public ExcludingFileFilter(ExcludeRule[] excludeRules) {
      this.excludeRules = excludeRules;
    }

    @Override
    public boolean accept(File file) {
      for (ExcludeRule excludeRule : excludeRules) {
        if (excludeRule.exclude(file)) {
          return false;
        }
      }
      return true;
    }
  }
}
