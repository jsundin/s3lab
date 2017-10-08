package s5lab.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationReader {
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

    Configuration configuration = new Configuration(
            loadedConf.getNotificationProviders(),
            parseJobs(loadedConf.getJobs()),
            loadedConf.getDatabase());
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

  private List<JobConfiguration> parseJobs(List<ParsedConfiguration.JobConf> jobs) throws ConfigurationException {
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

      JobConfiguration jobConf = new JobConfiguration(
              job.getDirectory(),
              job.getRetentionPolicy(),
              job.getIntervalInMinutes(),
              deletedFilesPolicy,
              oldVersionsPolicy
      );
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
