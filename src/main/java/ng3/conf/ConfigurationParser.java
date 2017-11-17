package ng3.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import s5lab.configuration.FileRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.*;

public class ConfigurationParser {
  private Random random = new SecureRandom();

  public Configuration parseConfiguration(File file) throws IOException {
    String ext = FilenameUtils.getExtension(file.getName());
    Format format;
    switch (ext.toLowerCase()) {
      case "json":
        format = Format.JSON;
        break;

      case "yml":
      case "yaml":
        format = Format.YAML;
        break;

      default:
        throw new IOException("Could not determine configuration format for '" + file + "'");
    }

    return parseConfiguration(file, format);
  }

  public Configuration parseConfiguration(File file, Format format) throws IOException {
    try (FileInputStream fis = new FileInputStream(file)) {
      return parseConfiguration(fis, format);
    }
  }

  public Configuration parseConfiguration(InputStream inputStream, Format format) throws IOException {
    ParsedConfiguration parsedConf = getObjectMapper(format).readValue(inputStream, ParsedConfiguration.class);

    List<DirectoryConfiguration> directoryConfigurations = new ArrayList<>();
    for (DirectoryConfiguration directoryConfiguration : parsedConf.getDirectories()) {
      List<FileRule> fileRules = new ArrayList<>();
      if (directoryConfiguration.getRules() != null) {
        fileRules.addAll(directoryConfiguration.getRules());
      }
      if (parsedConf.getGlobalRules() != null) {
        fileRules.addAll(parsedConf.getGlobalRules());
      }
      directoryConfigurations.add(new DirectoryConfiguration(
              directoryConfiguration.getDirectory(),
              fileRules,
              directoryConfiguration.getStoreAs(),
              directoryConfiguration.getVersioning()
      ));
    }

    // sanity check; driver must support versioning if versioning has been set
    try {
      if (parsedConf.getVersioningIntervalInMinutes() != null) {
        parsedConf.getBackupDriver().getVersioningDriver();
      }
    } catch (UnsupportedOperationException ignored) {
      throw new IOException("Versioning configured, but backup driver '" + parsedConf.getBackupDriver().getInformalName() + "' does not support versioning");
    }

    // sanity check; directory versioning is not allowed if global versioning has not been set
    if (parsedConf.getVersioningIntervalInMinutes() == null) {
      for (DirectoryConfiguration directoryConfiguration : directoryConfigurations) {
        if (directoryConfiguration.getVersioning() != null) {
          throw new IOException("Versioning has not been configured, but directory '" + directoryConfiguration.getDirectory() + "' has versioning configuration set");
        }
      }
    }

    return new Configuration(
            directoryConfigurations,
            parsedConf.getDatabase(),
            parsedConf.getIntervalInMinutes(),
            parsedConf.getVersioningIntervalInMinutes(),
            parsedConf.getBackupDriver(),
            parseSecrets(parsedConf.getSecrets()));
  }

  private Map<String, char[]> parseSecrets(Map<String, String> secrets) throws IOException {
    Map<String, char[]> out = new HashMap<>();
    if (secrets == null) {
      return out;
    }
    for (String k : secrets.keySet()) {
      String v = secrets.get(k);
      char[] password;
      if (k.endsWith("!")) {
        password = readFileToCharArray(v);
      } else {
        password = v.toCharArray();
      }
      out.put(k, password);
    }
    return out;
  }

  private char[] readFileToCharArray(String filename) throws IOException {
    File file;
    if (filename.startsWith("~")) {
      file = new File(System.getProperty("user.home"), filename.substring(1));
    } else {
      file = new File(filename);
    }
    return FileUtils.readFileToString(file, Charset.defaultCharset()).toCharArray();
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
