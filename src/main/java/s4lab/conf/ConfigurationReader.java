package s4lab.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import s4lab.fs.DirectoryConfiguration;
import s4lab.fs.rules.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author johdin
 * @since 2017-09-25
 */
public class ConfigurationReader {
  private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX";
  private final ObjectMapper objectMapper;

  public ConfigurationReader() {
    objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public Configuration readConfiguration(File file) throws IOException, ParseException {
    try (InputStream is = new FileInputStream(file)) {
      return readConfiguration(is);
    }
  }

  public Configuration readConfiguration(InputStream inputStream) throws IOException, ParseException {
    Configuration configuration = new Configuration();

    InternalConf c = objectMapper.readValue(inputStream, InternalConf.class);
    for (Map<String, String> ruleDef : c.getGlobalRules()) {
      configuration.getGlobalExcludeRules().add(parseRule(ruleDef));
    }
    for (InternalConf.DirectoryConf directoryConf : c.getDirectories()) {
      List<ExcludeRule> localRules = new ArrayList<>();
      if (directoryConf.getRules() != null) {
        for (Map<String, String> ruleDef : directoryConf.getRules()) {
          localRules.add(parseRule(ruleDef));
        }
      }

      DirectoryConfiguration d = new DirectoryConfiguration(directoryConf.getDirectory(), localRules.toArray(new ExcludeRule[0]));
      configuration.getDirectoryConfigurations().add(d);
    }

    return configuration;
  }

  private ExcludeRule parseRule(Map<String, String> rule) throws ParseException {
    if (!rule.containsKey("rule")) {
      throw new ParseException("Rule must have a type ('rule')", 0);
    }
    String ruleName = rule.get("rule");
    switch (ruleName) {
      case "excludeSymlinks":
        return new ExcludeSymlinksRule();

      case "excludeHiddenFiles":
        return new ExcludeHiddenFilesRule();

      case "excludePathPrefix":
        if (!rule.containsKey("prefix")) {
          throw new ParseException("'excludePathPrefix' must have property 'prefix'", 0);
        }
        String excludePathPrefix = rule.get("prefix");
        return new ExcludePathPrefixRule(excludePathPrefix);

      case "excludePathSuffix":
        if (!rule.containsKey("suffix")) {
          throw new ParseException("'excludePathSuffix' must have property 'suffix'", 0);
        }
        String excludePathSuffix = rule.get("suffix");
        return new ExcludePathSuffixRule(excludePathSuffix);

      case "excludeFilenamePrefix":
        if (!rule.containsKey("prefix")) {
          throw new ParseException("'excludeFilenamePrefix' must have property 'prefix'", 0);
        }
        String excludeFilenamePrefix = rule.get("prefix");
        return new ExcludeFilenamePrefixRule(excludeFilenamePrefix);

      case "excludeOldFiles":
        if (!rule.containsKey("cutoff")) {
          throw new ParseException("'excludeOldFiles' must have property 'cutoff'", 0);
        }
        String excludeOldFilesCutoff = rule.get("cutoff");
        LocalDateTime excludeOldFiles = LocalDateTime.ofInstant(new SimpleDateFormat(TIMESTAMP_FORMAT).parse(excludeOldFilesCutoff).toInstant(), ZoneId.systemDefault());
        return new ExcludeOldFilesRule(excludeOldFiles);

      default:
        throw new ParseException("Unknown rule '" + ruleName + "'", 0);
    }
  }

  public static class InternalConf {
    private List<DirectoryConf> directories;
    private List<Map<String, String>> globalRules;

    public List<DirectoryConf> getDirectories() {
      return directories;
    }

    public void setDirectories(List<DirectoryConf> directories) {
      this.directories = directories;
    }

    public List<Map<String, String>> getGlobalRules() {
      return globalRules;
    }

    public void setGlobalRules(List<Map<String, String>> globalRules) {
      this.globalRules = globalRules;
    }

    @Override
    public String toString() {
      return "InternalConf{" +
          "directories=" + directories +
          ", globalRules=" + globalRules +
          '}';
    }

    public static class DirectoryConf {
      private String directory;
      private List<Map<String, String>> rules;

      public String getDirectory() {
        return directory;
      }

      public void setDirectory(String directory) {
        this.directory = directory;
      }

      public List<Map<String, String>> getRules() {
        return rules;
      }

      public void setRules(List<Map<String, String>> rules) {
        this.rules = rules;
      }

      @Override
      public String toString() {
        return "DirectoryConf{" +
            "directory='" + directory + '\'' +
            ", rules=" + rules +
            '}';
      }
    }
  }
}
