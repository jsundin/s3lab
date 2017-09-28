package s4lab.fs;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.BackupAgent;
import s4lab.conf.RetentionPolicy;
import s4lab.db.DbHandler;
import s4lab.fs.rules.ExcludeRule;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class FileSystemScanner {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final DbHandler dbHandler;

  public FileSystemScanner(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  private int[] scanFiles(BackupAgent backupAgent, UUID directoryId, File directory, FileFilter fileFilter) {
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
        int[] subFoundFiles = scanFiles(backupAgent, directoryId, file, fileFilter);
        foundFiles[0] += subFoundFiles[0];
        foundFiles[1] += subFoundFiles[1];
      } else if (file.isFile()) {
        backupAgent.fileScanned(directoryId, file);
        foundFiles[1]++;
      } else {
        logger.warn("Unknown file type: " + file);
      }
    }

    return foundFiles;
  }

  private void scanForDeletes(BackupAgent backupAgent, Collection<ConfiguredDirectory> configuredDirectories) {
    Set<String> activeIds = configuredDirectories.stream()
        .filter(cd -> cd.isActive())
        .map(cd -> cd.getId().toString())
        .collect(Collectors.toSet());

    try (Connection c = dbHandler.getConnection()) {
      try (PreparedStatement s = c.prepareStatement("select f.filename, dc.id from file f join directory_config dc on dc.id=f.directory_id join file_version v on f.id=v.file_id where v.version=(select max(version) from file_version v2 where v2.file_id=f.id) and v.deleted=false")) {
        try (ResultSet rs = s.executeQuery()) {
          while (rs.next()) {
            String directoryId = rs.getString(2);
            if (!activeIds.contains(directoryId)) {
              continue;
            }

            File file = new File(rs.getString(1));
            if (file.exists() && file.isFile()) {
              continue;
            }

            backupAgent.fileScanned(UUID.fromString(directoryId), file);
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: vad gör vi här?
    }
  }

  private Collection<ConfiguredDirectory> scanConfiguredDirectories(List<DirectoryConfiguration> directoryConfigurations) {
    Map<String, ConfiguredDirectory> configuredDirectories = new HashMap<>();
    List<ConfiguredDirectory> retire = new ArrayList<>();

    try (Connection c = dbHandler.getConnection()) {
      try (PreparedStatement s = c.prepareStatement("select id, path, retention_policy from directory_config")) {
        try (ResultSet rs = s.executeQuery()) {
          while (rs.next()) {
            String id = rs.getString("id");
            String path = rs.getString("path");
            String retention_policy = rs.getString("retention_policy");

            ConfiguredDirectory cd = new ConfiguredDirectory();
            cd.setId(UUID.fromString(id));
            cd.setRetentionPolicy(retention_policy == null || retention_policy.isEmpty() ? null : RetentionPolicy.valueOf(retention_policy));
            List<DirectoryConfiguration> matchingDCs = directoryConfigurations.stream()
                .filter(dc -> dc.getDirectory().equals(path))
                .collect(Collectors.toList());
            if (matchingDCs.isEmpty()) {
              cd.setActive(false);
              retire.add(cd);
            } else if (matchingDCs.size() == 1) {
              cd.setActive(true);
              cd.setConfiguration(matchingDCs.get(0));
              configuredDirectories.put(path, cd);
            } else {
              throw new IllegalStateException("Too many results");
            }
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: what to do?
    }

    List<ConfiguredDirectory> add = directoryConfigurations.stream()
        .filter(dc -> !configuredDirectories.containsKey(dc.getDirectory()))
        .map(dc -> {
          ConfiguredDirectory cd = new ConfiguredDirectory();
          cd.setId(UUID.randomUUID());
          cd.setConfiguration(dc);
          cd.setRetentionPolicy(null);
          cd.setActive(true);
          return cd;
        })
        .collect(Collectors.toList());

    if (!add.isEmpty()) {
      try (Connection c = dbHandler.getConnection()) {
        try (PreparedStatement s = c.prepareStatement("insert into directory_config (id, path, retention_policy) values (?, ?, ?)")) {
          for (ConfiguredDirectory cd : add) {
            s.setString(1, cd.getId().toString());
            s.setString(2, cd.getConfiguration().getDirectory());
            s.setString(3, cd.getRetentionPolicy() == null ? null : cd.getRetentionPolicy().toString());
            s.executeUpdate();
            configuredDirectories.put(cd.getConfiguration().getDirectory(), cd);
          }
        }
      } catch (SQLException e) {
        throw new RuntimeException(e); // TODO: what to do?
      }
    }

    // TODO: retire - process depends on retention policy. alternatives:
    //   1) leave everything as is, just ignore (could be suitable for removable drives)
    //   2) remove files from database, leave on remote host
    //   3) remove files from database and remote host
    //   4) fail

    return configuredDirectories.values();
  }

  public void scan(BackupAgent backupAgent, List<DirectoryConfiguration> directoryConfigurations, ExcludeRule... systemExcludeRules) {
    ZonedDateTime scanTime = ZonedDateTime.now();

    logger.info("Started scanning {} configurations at {}", directoryConfigurations.size(), scanTime);
    backupAgent.fileScanStarted();
    Collection<ConfiguredDirectory> configuredDirectories = scanConfiguredDirectories(directoryConfigurations);
    long t0 = System.currentTimeMillis();
    int[] foundFiles = {0, 0};

    for (ConfiguredDirectory configuredDirectory : configuredDirectories) {
      if (!configuredDirectory.isActive()) {
        continue;
      }

      File directory = new File(configuredDirectory.getConfiguration().getDirectory());
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

      ArrayList<ExcludeRule> mergedRules = new ArrayList<>(Arrays.asList(systemExcludeRules));
      mergedRules.addAll(configuredDirectory.getConfiguration().getExcludeRules());
      int[] subFoundFiles = scanFiles(backupAgent, configuredDirectory.getId(), directory, new ExcludingFileFilter(mergedRules));
      foundFiles[0] += subFoundFiles[0];
      foundFiles[1] += subFoundFiles[1];
    }

    scanForDeletes(backupAgent, configuredDirectories);

    logger.info("Finished scanning {} configurations, tagged {} of {} files in {}ms", directoryConfigurations.size(), foundFiles[1], foundFiles[0], System.currentTimeMillis() - t0);
    dbHandler.setStateInformationLastScan(scanTime);
    backupAgent.fileScanEnded();
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

  private class ConfiguredDirectory {
    private UUID id;
    private DirectoryConfiguration configuration;
    private RetentionPolicy retentionPolicy;
    private boolean active;

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public DirectoryConfiguration getConfiguration() {
      return configuration;
    }

    public void setConfiguration(DirectoryConfiguration configuration) {
      this.configuration = configuration;
    }

    public RetentionPolicy getRetentionPolicy() {
      return retentionPolicy;
    }

    public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
      this.retentionPolicy = retentionPolicy;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }
  }
}
