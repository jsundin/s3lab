package s5lab.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.backuptarget.BackupProvider;
import s5lab.backuptarget.JobTargetConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigurationReader {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public Configuration read(File file) throws IOException {
    String extension = FilenameUtils.getExtension(file.getName());
    Format format;
    switch (extension) {
      case "yml":
      case "yaml":
        format = Format.YAML;
        break;

      case "json":
        format = Format.JSON;
        break;

      default:
        throw new IOException("Unknown file type: " + file);
    }

    return read(file, format);
  }

  public Configuration read(File file, Format format) throws IOException {
    try (InputStream fis = new FileInputStream(file)) {
      return read(fis, format);
    }
  }

  public Configuration read(InputStream inputStream, Format format) throws IOException {
    ParsedConfiguration loadedConf = getObjectMapper(format).readValue(inputStream, ParsedConfiguration.class);
    List<BackupProvider> backupProviders = loadedConf.getBackupProviders();
    List<JobConfiguration> jobs = parseJobs(loadedConf.getJobs(), backupProviders);

    Configuration configuration = new Configuration(
            loadedConf.getNotificationProviders(),
            jobs,
            loadedConf.getDatabase(),
            backupProviders);
    return configuration;
  }

  private JobConfiguration.FileVersioningPolicy parseFileVersioningPolicy(Boolean keepForever, Boolean deleteOldVersions, Integer keepVersions, Long expireAfterMinutes) throws ConfigurationException {
    JobConfiguration.FileVersioningPolicy policy;

    if (keepForever != null && keepForever) {
      policy = new JobConfiguration.FileVersioningPolicy(JobConfiguration.FileVersioningPolicyType.KEEP_FOREVER);
    } else if (deleteOldVersions != null && deleteOldVersions) {
      policy = new JobConfiguration.FileVersioningPolicy(JobConfiguration.FileVersioningPolicyType.DELETE);
    } else if (keepVersions != null || expireAfterMinutes != null) {
      policy = new JobConfiguration.FileVersioningPolicy(
              JobConfiguration.FileVersioningPolicyType.EXPIRE,
              keepVersions,
              expireAfterMinutes
      );
    } else {
      throw new ConfigurationException("Cannot parse versioning policy");
    }

    return policy;
  }

  private List<JobConfiguration> parseJobs(List<ParsedConfiguration.JobConf> jobs, List<BackupProvider> backupProviders) throws ConfigurationException {
    Map<String, BackupProvider> backupProvidersById = backupProviders.stream().collect(Collectors.toMap(k -> k.getId(), v -> v));

    List<JobConfiguration> jobConfigurations = new ArrayList<>();

    for (ParsedConfiguration.JobConf job : jobs) {
      if (job.getDirectory() == null) {
        throw new ConfigurationException("Job configured without a directory");
      }
      if (job.getRetentionPolicy() == null) {
        throw new ConfigurationException("No retention-policy for job '" + job.getDirectory() + "'");
      }
      if (job.getIntervalInMinutes() == null) {
        throw new ConfigurationException("No interval configured for job '" + job.getDirectory() + "'");
      }
      if (job.getDeletedFilesPolicy() == null) {
        throw new ConfigurationException("No deleted-files-policy for job '" + job.getDirectory() + "'");
      }
      if (job.getOldVersionsPolicy() == null) {
        throw new ConfigurationException("No old-versions-policy for job '" + job.getDirectory() + "'");
      }
      JobConfiguration.FileVersioningPolicy deletedFilesPolicy = parseFileVersioningPolicy(
              job.getDeletedFilesPolicy().getKeepForever(),
              job.getDeletedFilesPolicy().getDelete(),
              job.getDeletedFilesPolicy().getKeepVersions(),
              job.getDeletedFilesPolicy().getExpireAfterMinutes());

      JobConfiguration.FileVersioningPolicy oldVersionsPolicy = parseFileVersioningPolicy(
              job.getOldVersionsPolicy().getKeepForever(),
              job.getOldVersionsPolicy().getDeleteOldVersions(),
              job.getOldVersionsPolicy().getKeepOldVersions(),
              job.getOldVersionsPolicy().getExpireAfterMinutes());

      if (job.getTargetConfiguration() == null || !job.getTargetConfiguration().containsKey("id")) {
        throw new ConfigurationException("No target for job '" + job.getDirectory() + "'");
      }

      HashMap<String, Object> targetConfiguration = new HashMap<>(job.getTargetConfiguration());
      String backupTargetId = (String) targetConfiguration.get("id");
      targetConfiguration.remove("id");
      if (!backupProvidersById.containsKey(backupTargetId)) {
        throw new ConfigurationException("Target '" + backupTargetId + "' does not exist for job '" + job.getDirectory() + "'");
      }
      BackupProvider backupProvider = backupProvidersById.get(backupTargetId);
      JobTargetConfiguration targetConfig = backupProvider.parseJobTargetConfiguration(targetConfiguration);

      JobConfiguration jobConf = new JobConfiguration(
              job.getDirectory(),
              job.getRetentionPolicy(),
              job.getIntervalInMinutes(),
              deletedFilesPolicy,
              oldVersionsPolicy,
              backupProvider,
              targetConfig);
      jobConfigurations.add(jobConf);
    }
    return jobConfigurations;
  }

  private ObjectMapper getObjectMapper(Format format) throws IOException {
    ObjectMapper objectMapper;
    switch (format) {
      case JSON:
        objectMapper = new ObjectMapper();
        break;

      case YAML:
        objectMapper = new ObjectMapper(new YAMLFactory());
        break;

      default:
        throw new IOException("Unsupported format: " + format);
    }
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    return objectMapper;
  }

  public enum Format {
    JSON,
    YAML
  }
}
