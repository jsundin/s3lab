package s5lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.BackupException;
import s5lab.BackupJob;
import s5lab.configuration.FileRule;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class BackupJobRunner implements Callable<Void> {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BackupJob job;
  private final BackupAgentContext ctx;

  public BackupJobRunner(BackupJob job, BackupAgentContext ctx) {
    this.job = job;
    this.ctx = ctx;
  }

  @Override
  public Void call() throws Exception {
    long t0 = System.currentTimeMillis();
    Stats stats = scanDirectory(job.getConfiguration().getDirectory(), job.getConfiguration().getFileRules());
    logger.debug("Scanned '{}' in {}ms with {} warnings: {} entries and {} accepted files", job.getConfiguration().getDirectory(), (System.currentTimeMillis() - t0), stats.warnings, stats.foundFiles, stats.acceptedFiles);
    return null;
  }

  private Stats scanDirectory(File directory, List<FileRule> fileRules) throws BackupException {
    if (!directory.exists()) {
      logger.error("Directory does not exist: '{}'", directory);
      throw new BackupException("Directory does not exist: " + directory);
    }

    if (!directory.isDirectory()) {
      logger.error("Directory is not a directory: '{}'", directory);
      throw new BackupException("Directory is not a directory: " + directory);
    }

    Stats stats = new Stats();
    File[] files = directory.listFiles();
    if (files == null) {
      logger.warn("Could not access directory '{}'", directory);
      stats.warnings++;
      return stats;
    }

    for (File file : files) {
      stats.foundFiles++;
      boolean accepted = true;
      for (FileRule fileRule : fileRules) {
        if (!fileRule.accept(file)) {
          accepted = false;
          break;
        }
      }
      if (!accepted) {
        continue;
      }

      if (file.isDirectory()) {
        stats.add(scanDirectory(file, fileRules));
      } else if (file.isFile()) {
        stats.acceptedFiles++;
        ctx.filescannerQueue.add(new FilescannerEvent(job.getId(), file));
      } else {
        stats.warnings++;
        logger.warn("Unknown file type for '{}'", file);
      }
    }
    return stats;
  }

  private class Stats {
    int foundFiles;
    int acceptedFiles;
    int warnings;
    void add(Stats other) {
      foundFiles += other.foundFiles;
      acceptedFiles += other.acceptedFiles;
      warnings += other.warnings;
    }
  }
}
