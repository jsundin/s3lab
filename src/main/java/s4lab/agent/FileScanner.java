package s4lab.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.FileTools;
import s4lab.conf.RetentionPolicy;
import s4lab.db.DatabaseException;
import s4lab.db.DbHandler;
import s4lab.fs.DirectoryConfiguration;
import s4lab.fs.rules.ExcludeOldFilesRule;
import s4lab.fs.rules.ExcludeRule;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class FileScanner {
  private final DbHandler dbHandler;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static int threadIndex = 0;

  public FileScanner(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public void scan(final List<DirectoryConfiguration> directoryConfigurations, boolean forceFullScan) {
    long t0 = System.currentTimeMillis();
    Collection<ConfiguredDirectory> configuredDirectories = scanConfiguredDirectories(directoryConfigurations);

    List<Thread> threads = new ArrayList<>();
    configuredDirectories.forEach(cd -> {
      FileScannerThread thread = new FileScannerThread(cd, forceFullScan);
      threads.add(thread);
      thread.start();
    });
    for (Thread thread : threads) {
      boolean wasInterrupted = false;
      do {
        try {
          Thread.interrupted(); // clear interrupt flag
          thread.join();
        } catch (InterruptedException ignored) {
          wasInterrupted = true;
        }
      } while (wasInterrupted);
    }

    logger.info("Filescan finished in {}ms", (System.currentTimeMillis() - t0));
  }

  private int[] scanDirectory(ConfiguredDirectory configuredDirectory, File directory, FileFilter fileFilter) {
    if (!directory.exists()) {
      logger.error("Directory does not exist: '{}'", directory);
      throw new IllegalArgumentException("No such directory");
    }

    if (!directory.isDirectory()) {
      logger.error("Directory is not a directory: '{}'", directory);
      throw new IllegalArgumentException("No such directory");
    }

    File[] files = directory.listFiles();
    int[] results = {0, 0};
    if (files == null) {
      logger.warn("Cannot find any files  in '" + directory + "'");
      return results;
    }

    for (File file : files) {
      results[0]++;
      if (!fileFilter.accept(file)) {
        continue;
      }

      if (file.isDirectory()) {
        int[] subResults = scanDirectory(configuredDirectory, file, fileFilter);
        results[0] += subResults[0];
        results[1] += subResults[1];
      } else if (file.isFile()) {
        results[1]++;
        scanFile(configuredDirectory, file);
      } else {
        logger.warn("Unknown file type: " + file);
      }
    }
    return results;
  }

  private int scanForDeletes(ConfiguredDirectory configuredDirectory) {
    List<File> deletedFiles = new ArrayList<>();
    try (Connection c = dbHandler.getConnection()) {
      try (PreparedStatement s = c.prepareStatement("select f.filename from file f join directory_config dc on dc.id=f.directory_id join file_version v on f.id=v.file_id where v.version=(select max(version) from file_version v2 where v2.file_id=f.id) and v.deleted=false and dc.id=?")) {
        s.setString(1, configuredDirectory.id.toString());
        try (ResultSet rs = s.executeQuery()) {
          while (rs.next()) {
            File file = new File(rs.getString(1));
            if (!file.exists() || !file.isFile()) {
              deletedFiles.add(file);
            }
          }
        }
      }
    } catch (SQLException e) {
      throw new DatabaseException(e);
    }

    deletedFiles.forEach(file -> scanFile(configuredDirectory, file));
    return deletedFiles.size();
  }

  private void scanFile(ConfiguredDirectory configuredDirectory, File file) {
    FileVersion fileVersion = dbHandler.buildQuery("select f.id, f.filename, v.version, v.modified, v.deleted from file f join directory_config cd on cd.id=f.directory_id join file_version v on f.id=v.file_id where cd.id=? and f.filename=? and v.version=(select max(version) from file_version vm where vm.file_id=f.id)")
            .withParam().uuidValue(1, configuredDirectory.id)
            .withParam().fileValue(2, file)
            .executeQueryForObject(rs -> {
              FileVersion fv = new FileVersion();
              fv.id = rs.getUuid(1);
              fv.file = rs.getFile(2);
              fv.version = rs.getInt(3);
              fv.modified = rs.getTimestamp(4);
              fv.deleted = rs.getBoolean(5);
              return fv;
            });

    if (fileVersion == null) {
      UUID fileId = UUID.randomUUID();
      ZonedDateTime lastModified = FileTools.lastModified(file);

      insertFile(fileId, file, configuredDirectory.id);
      insertFileVersion(fileId, 0, lastModified, false);
    } else {
      if (!file.exists()) {
        insertFileVersion(fileVersion.id, fileVersion.version + 1, ZonedDateTime.now(), true);
      } else {
        ZonedDateTime lastModified = FileTools.lastModified(file);
        if (!fileVersion.modified.equals(lastModified)) {
          insertFileVersion(fileVersion.id, fileVersion.version + 1, lastModified, false);
        }
      }
    }
  }

  private int insertFile(UUID fileId, File file, UUID directoryId) {
    return dbHandler.buildQuery("insert into file (id, filename, directory_id) values (?, ?, ?)")
            .withParam().uuidValue(1, fileId)
            .withParam().fileValue(2, file)
            .withParam().uuidValue(3, directoryId)
            .executeUpdate();
  }

  private int insertFileVersion(UUID fileId, int version, ZonedDateTime modified, boolean deleted) {
    return dbHandler.buildQuery("insert into file_version (file_id, version, modified, deleted) values (?, ?, ?, ?)")
            .withParam().uuidValue(1, fileId)
            .withParam().intValue(2, version)
            .withParam().timestampValue(3, modified)
            .withParam().booleanValue(4, deleted)
            .executeUpdate();
  }

  private Collection<ConfiguredDirectory> scanConfiguredDirectories(List<DirectoryConfiguration> directoryConfigurations) {
    Map<File, ConfiguredDirectory> configuredDirectories = dbHandler
            .buildQuery("select id, path, retention_policy, last_scan from directory_config")
            .executeQuery(rs -> {
              ConfiguredDirectory cd = new ConfiguredDirectory();
              cd.id = rs.getUuid(1);
              cd.path = rs.getFile(2);
              cd.retentionPolicy = RetentionPolicy.valueOf(rs.getString(3));
              cd.lastScan = rs.getTimestamp(4);
              return cd;
            }).stream()
            .collect(Collectors.toMap(k -> k.path, v -> v));

    List<ConfiguredDirectory> removedFromConf = configuredDirectories.entrySet().stream()
            .filter(v -> {
              for (DirectoryConfiguration dc : directoryConfigurations) {
                if (dc.getDirectory().equals(v.getKey())) {
                  return false;
                }
              }
              return true;
            })
            .map(v -> v.getValue())
            .collect(Collectors.toList());

    Map<File, ConfiguredDirectory> addedToConf = new HashMap<>();
    List<ConfiguredDirectory> updatedInConf = new ArrayList<>();
    for (DirectoryConfiguration dc : directoryConfigurations) {
      if (configuredDirectories.containsKey(dc.getDirectory())) {
        ConfiguredDirectory cd = configuredDirectories.get(dc.getDirectory());
        cd.excludeRules = dc.getExcludeRules().toArray(new ExcludeRule[0]);
        if (!cd.retentionPolicy.equals(dc.getRetentionPolicy())) {
          updatedInConf.add(cd);
        }
        cd.retentionPolicy = dc.getRetentionPolicy();
      } else {
        ConfiguredDirectory cd = new ConfiguredDirectory();
        cd.id = UUID.randomUUID();
        cd.path = dc.getDirectory();
        cd.retentionPolicy = dc.getRetentionPolicy();
        cd.excludeRules = dc.getExcludeRules().toArray(new ExcludeRule[0]);
        addedToConf.put(cd.path, cd);
      }
    }

    for (ConfiguredDirectory remove : removedFromConf) {
      switch (remove.retentionPolicy) {
        case FAIL:
          logger.error("Cannot remove directory '{}' with retention policy 'FAIL'");
          throw new IllegalStateException("Directory cannot be removed: " + remove.path);

        case IGNORE:
          logger.warn("Directory '{}' was removed and has been disabled", remove.path);
          configuredDirectories.remove(remove.path);
          break;

        case CLEAR_LOCAL:
          logger.warn("Directory '{}' was removed, and local backup information will be purged", remove.path);
          throw new UnsupportedOperationException("This has not yet been implemented"); // TODO: implement

        case CLEAR_EVERYTHING:
          logger.warn("Directory '{}' was removed, and backups will be purged", remove.path);
          throw new UnsupportedOperationException("This has not yet been implemented"); // TODO: implement

        default:
          logger.error("Unknown retention policy: '{}'", remove.retentionPolicy);
          throw new IllegalStateException("Unsupported retention policy: " + remove.retentionPolicy);
      }
    }

    dbHandler.buildQuery("insert into directory_config (id, path, retention_policy) values (?, ?, ?)")
            .executeUpdate(addedToConf.values(), (v, s) -> {
              s.uuidValue(1, v.id);
              s.fileValue(2, v.path);
              s.stringValue(3, v.retentionPolicy == null ? null : v.retentionPolicy.toString());
              logger.info("Directory '{}' has been added", v.path);
            });
    configuredDirectories.putAll(addedToConf);

    dbHandler.buildQuery("update directory_config set retention_policy=? where id=?")
            .executeUpdate(updatedInConf, (v, s) -> {
              s.uuidValue(2, v.id);
              s.stringValue(1, v.retentionPolicy.toString());
              logger.info("Directory '{}' has been updated ({})", v.path, v.retentionPolicy);
            });

    return configuredDirectories.values();
  }

  private class FileScannerThread extends Thread {
    private final ConfiguredDirectory configuredDirectory;
    private final boolean forceFullScan;

    private FileScannerThread(ConfiguredDirectory configuredDirectory, boolean forceFullScan) {
      super("FileScanner-" + (threadIndex++));
      this.configuredDirectory = configuredDirectory;
      this.forceFullScan = forceFullScan;
    }

    @Override
    public void run() {
      ZonedDateTime scanTime = ZonedDateTime.now();
      if (forceFullScan || configuredDirectory.lastScan == null) {
        logger.info("Initial filescan started for '{}'", configuredDirectory.path);
      } else {
        logger.info("Supplementary filescan started for '{}', last scanned {}", configuredDirectory.path, configuredDirectory.lastScan);
      }
      long t0 = System.currentTimeMillis();

      List<ExcludeRule> allRules = new ArrayList<>(Arrays.asList(configuredDirectory.excludeRules));
      if (configuredDirectory.lastScan != null) {
        allRules.add(new ExcludeOldFilesRule(configuredDirectory.lastScan));
      }
      ExcludingFileFilter fileFilter = new ExcludingFileFilter(allRules);
      int[] results;

      try {
        results = scanDirectory(configuredDirectory, configuredDirectory.path, fileFilter);
      } catch (Throwable t) {
        logger.error("File scan failed for '{}'", t);
        return;
      }

      int deletes = scanForDeletes(configuredDirectory);
      logger.info("Filescan finished for '{}' in {}ms, tagged {}/{} files and removed {}", configuredDirectory.path, (System.currentTimeMillis() - t0), results[1], results[0], deletes);

      dbHandler.buildQuery("update directory_config set last_scan=? where id=?")
              .withParam().timestampValue(1, scanTime)
              .withParam().uuidValue(2, configuredDirectory.id)
              .executeUpdate();
    }
  }

  private class ConfiguredDirectory {
    private UUID id;
    private File path;
    private RetentionPolicy retentionPolicy;
    private ExcludeRule[] excludeRules;
    private ZonedDateTime lastScan;
  }

  private class FileVersion {
    private UUID id;
    private File file;
    private int version;
    private ZonedDateTime modified;
    private boolean deleted;
  }

  private interface FileFilter {
    boolean accept(File file);
  }

  private class ExcludingFileFilter implements FileFilter {
    private final List<ExcludeRule> excludeRules;

    public ExcludingFileFilter(List<ExcludeRule> excludeRules) {
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
