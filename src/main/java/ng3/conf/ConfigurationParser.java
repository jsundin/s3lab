package ng3.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ConfigurationParser {
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

    return new Configuration(
            parsedConf.getDirectories(),
            parsedConf.getDatabase(),
            parsedConf.getIntervalInMinutes());
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
