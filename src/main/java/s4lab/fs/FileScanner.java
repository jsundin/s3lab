package s4lab.fs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FileScanner {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private Collection<File> scan(File dir, IOFileFilter fileFilter) {
    Collection<File> allFiles = FileUtils.listFiles(dir, fileFilter, fileFilter);
    return allFiles;
  }

  public List<File> scan(List<DirectoryConfiguration> directoryConfigurations, ExcludeRule... globalExcludeRules) {
    List<File> allFiles = new ArrayList<>();
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
      Collection<File> files = scan(directory, new ExcludingFileFilter(mergedRules));
      allFiles.addAll(files);
    }
    return allFiles;
  }

  private class ExcludingFileFilter implements IOFileFilter {
    private final ExcludeRule[] excludeRules;

    public ExcludingFileFilter(ExcludeRule[] excludeRules) {
      this.excludeRules = excludeRules;
    }

    @Override
    public boolean accept(File file) {
      return !applyExcludeRules(file, excludeRules);
    }

    @Override
    public boolean accept(File file, String s) {
      throw new RuntimeException("Should this happen? I don't know?");
    }

    private boolean applyExcludeRules(File f, ExcludeRule[] excludeRules) {
      for (ExcludeRule excludeRule : excludeRules) {
        if (excludeRule.exclude(f)) {
          return true;
        }
      }
      return false;
    }
  }
}
