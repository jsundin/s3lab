package ng3.drivers.filecopy;

import ng3.BackupDirectory;
import ng3.common.SimpleThreadFactory;
import ng3.conf.Configuration;
import ng3.conf.VersioningConfiguration;
import ng3.db.DbClient;
import ng3.drivers.VersioningDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.FileTools;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author johdin
 * @since 2017-11-17
 */
public class FileCopyVersioningDriver implements VersioningDriver {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Pattern versionPattern = Pattern.compile("^(?<version>[0-9]+).*");
  private final File path;
  private final int threads;

  public FileCopyVersioningDriver(File path, int threads) {
    this.path = path;
    this.threads = threads;
  }

  @Override
  public void performVersioning(DbClient dbClient, Configuration configuration, List<BackupDirectory> backupDirectories) {
    ExecutorService executor = Executors.newFixedThreadPool(threads, new SimpleThreadFactory("FileCopyVersioning"));
    for (BackupDirectory backupDirectory : backupDirectories) {
      executor.submit(() -> {
        try {
          performVersioning(dbClient, configuration, backupDirectory);
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

  private void performVersioning(DbClient dbClient, Configuration configuration, BackupDirectory backupDirectory) {
    File target;
    if (backupDirectory.getConfiguration().getStoreAs() == null) {
      target = new File(path, backupDirectory.getConfiguration().getDirectory().toString());
    } else {
      target = new File(path, backupDirectory.getConfiguration().getStoreAs());
    }

    performVersioning(target, backupDirectory);
  }

  private void performVersioning(File directory, BackupDirectory backupDirectory) {
    File[] files = directory.listFiles();
    if (files == null) {
      logger.warn("Could not access directory '{}'", directory);
      return;
    }

    for (File file : files) {
      if (file.isDirectory() && file.getName().startsWith(FileCopyBackupDriver.FILE_PREFIX)) {
        performFileVersioning(file, backupDirectory);
      } else if (file.isDirectory()) {
        performVersioning(file, backupDirectory);
      } else {
        logger.warn("Unexpected file '{}' - ignored", file);
      }
    }
  }

  private void performFileVersioning(File dir, BackupDirectory backupDirectory) {
    File[] files = dir.listFiles();
    if (files == null) {
      logger.warn("Could not access file versions for '{}'", dir);
      return;
    }

    if (files.length == 0) {
      // TODO: vi kan ta bort den här katalogen
      logger.warn("No versions for '{}'", dir);
      return;
    }

    /*Map<Integer, VersionDef> versions = new HashMap<>();
    for (File file : files) {
      String filename = file.getName();
      Matcher m = versionPattern.matcher(filename);
      if (!m.matches()) {
        logger.warn("Could not extract version information for file '{}'", file);
        return;
      }

      int version = Integer.parseInt(m.group("version"));
      if (!versions.containsKey(version)) {
        versions.put(version, new VersionDef());
      }
      versions.get(version).addFile(file);
    }

    List<Integer> orderedVersions = new ArrayList<>(versions.keySet());
    Collections.sort(orderedVersions);
    Collections.reverse(orderedVersions);

    VersionDef lastVersion = versions.get(orderedVersions.get(0));
    if (lastVersion.isDeleted()) {
      if (backupDirectory.getConfiguration().getVersioning().getDeletedFileStrategy() != null) {
        versionDeletedFile(dir, backupDirectory.getConfiguration().getVersioning().getDeletedFileStrategy(), orderedVersions, versions);
      }
    } else {
      if (backupDirectory.getConfiguration().getVersioning().getFileStrategy() != null) {
        // TODO: old-strategy
      }
    }*/
  }

  private void versionDeletedFile(File dir, VersioningConfiguration.DeletedFileStrategy strategy, List<Integer> orderedVersions, Map<Integer, VersionDef> versions) {
    List<Integer> versionsToDelete = new ArrayList<>();
    VersionDef lastVersion = versions.get(orderedVersions.get(0));

    if (strategy.getStrategy() == VersioningConfiguration.DeletedFileStrategy.Strategy.DELETE_FILE) {
      if (strategy.getAfterMinutes() == null) {
        // ta bort alla versioner
        versionsToDelete.addAll(orderedVersions);
      } else {
        // om SENASTE versionen är äldre än (nu-after): ta bort alla versioner
        if (lastVersion.lastModified.isBefore(ZonedDateTime.now().minusMinutes(strategy.getAfterMinutes()))) {
          versionsToDelete.addAll(orderedVersions);
        }
      }
    } else if (strategy.getStrategy() == VersioningConfiguration.DeletedFileStrategy.Strategy.DELETE_HISTORY) {
      // om SENASTE versionen är äldre än (nu-after): ta bort alla versioner fram TILL senaste levande (dvs, senaste levande + borttag ska vara kvar)
      if (strategy.getAfterMinutes() != null && strategy.getAgeInMinutes() == null && strategy.getRetainVersions() == null) {
        if (lastVersion.lastModified.isBefore(ZonedDateTime.now().minusMinutes(strategy.getAfterMinutes()))) {
          boolean foundLiving = false;
          for (Integer version : orderedVersions) {
            if (!foundLiving && !versions.get(version).isDeleted()) {
              foundLiving = true;
              continue;
            }

            if (foundLiving) {
              versionsToDelete.add(version);
            }
          }
        }
      } else if (strategy.getAfterMinutes() != null && strategy.getAgeInMinutes() != null && strategy.getRetainVersions() == null) {
        // om SENASTE versionen är äldre än (nu-after): ta bort alla versioner som är äldre än (age)
        if (lastVersion.lastModified.isBefore(ZonedDateTime.now().minusMinutes(strategy.getAfterMinutes()))) {
          for (Integer version : orderedVersions) {
            if (versions.get(version).lastModified.isBefore(ZonedDateTime.now().minusMinutes(strategy.getAgeInMinutes()))) {
              versionsToDelete.add(version);
            }
          }
        }
      } else if (strategy.getRetainVersions() != null && strategy.getAfterMinutes() == null && strategy.getAgeInMinutes() == null) {
        // ta bort alla versioner som har högre index än (retain)
        for (int i = strategy.getRetainVersions(); i < orderedVersions.size(); i++) {
          versionsToDelete.add(orderedVersions.get(i));
        }
      } else if (strategy.getAfterMinutes() != null && strategy.getRetainVersions() != null && strategy.getAgeInMinutes() == null) {
        // om SENASTE versionen är äldre än (nu-after): ta bort alla versioner som har högre index än (retain)
        if (lastVersion.lastModified.isBefore(ZonedDateTime.now().minusMinutes(strategy.getAfterMinutes()))) {
          for (int i = strategy.getRetainVersions(); i < orderedVersions.size(); i++) {
            versionsToDelete.add(orderedVersions.get(i));
          }
        }
      } else {
        throw new IllegalArgumentException("Unknown strategy");
      }
    } else {
      throw new IllegalArgumentException("Unknown strategy");
    }

    System.out.println("ORDERED: " + orderedVersions);
    System.out.println("DELETED: " + versionsToDelete);
  }

  private class VersionDef {
    private final Set<File> files = new HashSet<>();
    private boolean deleted;
    private ZonedDateTime lastModified;

    private void addFile(File file) {
      files.add(file);
      if (file.getName().endsWith(FileCopyBackupDriver.DELETED_EXTENSION)) {
        deleted = true;
      }
      if (versionPattern.matcher(file.getName()).matches()) {
        lastModified = FileTools.lastModified(file);
      }
    }

    public boolean isDeleted() {
      return deleted;
    }

    public ZonedDateTime getLastModified() {
      return lastModified;
    }
  }
}
