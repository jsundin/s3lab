package s4lab.fs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Queue;

public class FileSystemScanner {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private int scan(Queue<File> targetQueue, File directory, FileFilter fileFilter) {
    File[] files = directory.listFiles();
    if (files == null) {
      logger.warn("Cannot find any files  in '" + directory + "'");
      return 0;
    }

    int foundFiles = 0;
    for (File file : files) {
      if (!fileFilter.accept(file)) {
        continue;
      }

      if (file.isDirectory()) {
        foundFiles += scan(targetQueue, file, fileFilter);
      } else if (file.isFile()) {
        targetQueue.add(file);
        foundFiles++;
      } else {
        logger.warn("Unknown file type: " + file);
      }
    }

    return foundFiles;
  }

  public void scan(Queue<File> targetQueue, List<DirectoryConfiguration> directoryConfigurations, ExcludeRule... globalExcludeRules) {
    logger.info("Started scanning " + directoryConfigurations.size() + " configurations");
    long t0 = System.currentTimeMillis();
    int foundFiles = 0;

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
      foundFiles += scan(targetQueue, directory, new ExcludingFileFilter(mergedRules));
    }

    logger.info("Finished scanning " + directoryConfigurations.size() + " configurations, found " + foundFiles + " files in " + (System.currentTimeMillis() - t0) + "ms");
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
