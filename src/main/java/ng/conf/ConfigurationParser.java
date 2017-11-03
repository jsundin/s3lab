package ng.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class ConfigurationParser {
  private static final Format DEFAULT_FORMAT = Format.JSON;

  public Configuration parse(File file) throws IOException {
    String ext = FilenameUtils.getExtension(file.getName());
    Format format;

    switch (ext) {
      case "yml":
      case "yaml":
        format = Format.YAML;
        break;

      case "json":
        format = Format.JSON;
        break;

      default:
        throw new IOException("Unknown file type for '" + file.toString() + "'");
    }

    return parse(file, format);
  }

  public Configuration parse(File file, Format format) throws IOException {
    try (InputStream fis = new FileInputStream(file)) {
      return parse(fis, format);
    }
  }

  public Configuration parse(InputStream inputStream) throws IOException {
    return parse(inputStream, DEFAULT_FORMAT);
  }

  public Configuration parse(InputStream inputStream, Format format) throws IOException {
    ParsedConf parsedConf = getObjectMapper(format).readValue(inputStream, ParsedConf.class);

    Configuration conf = new Configuration(
        parsedConf.getDirectories(),
        parsedConf.getDatabase());
    return conf;
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
