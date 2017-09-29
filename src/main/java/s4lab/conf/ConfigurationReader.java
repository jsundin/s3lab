package s4lab.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.fs.DirectoryConfiguration;
import s4lab.fs.rules.ExcludeRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author johdin
 * @since 2017-09-25
 */
public class ConfigurationReader {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private Map<String, RuleDefinition> scannedRules;

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
    List<ExcludeRule> globalExcludeRules = new ArrayList<>();

    JsonConf jc = objectMapper.readValue(inputStream, JsonConf.class);
    for (Map<String, String> ruleDef : jc.getGlobalRules()) {
      globalExcludeRules.add(parseRule(ruleDef));
    }
    for (JsonConf.DirectoryConf directoryConf : jc.getDirectories()) {
      List<ExcludeRule> localRules = new ArrayList<>();
      if (directoryConf.getRules() != null) {
        for (Map<String, String> ruleDef : directoryConf.getRules()) {
          localRules.add(parseRule(ruleDef));
        }
      }

      localRules.addAll(globalExcludeRules);
      DirectoryConfiguration d = new DirectoryConfiguration(new File(directoryConf.getDirectory()), localRules.toArray(new ExcludeRule[0]));
      d.setRetentionPolicy(directoryConf.retentionPolicy);
      configuration.getDirectoryConfigurations().add(d);
    }

    return configuration;
  }

  private Map<String, RuleDefinition> scanForRules() {
    Reflections r = new Reflections(new ConfigurationBuilder()
        .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner())
        .setUrls(ClasspathHelper.forPackage(ExcludeRule.class.getPackage().getName())));
    Set<Class<?>> ruleTypes = r.getTypesAnnotatedWith(Rule.class);
    Map<String, RuleDefinition> rules = new HashMap<>();
    for (Class<?> ruleType : ruleTypes) {
      Rule ruleAnnotation = ruleType.getAnnotation(Rule.class);
      if (ruleAnnotation == null) {
        continue;
      }

      RuleDefinition rd = new RuleDefinition();
      rd.type = ruleType;

      Method[] methods = ruleType.getDeclaredMethods();
      for (Method method : methods) {
        RuleParam ruleParam = method.getAnnotation(RuleParam.class);
        if (ruleParam == null) {
          continue;
        }

        RuleDefinition.PropertyDefinition pd = rd.new PropertyDefinition();
        pd.method = method;
        pd.annotation = ruleParam;
        rd.properties.put(ruleParam.value(), pd);
      }
      rules.put(ruleAnnotation.value(), rd);
    }
    return rules;
  }

  private ExcludeRule parseRule(Map<String, String> ruleDef) throws ParseException {
    if (scannedRules == null) {
      scannedRules = scanForRules();
    }

    if (!ruleDef.containsKey("rule")) {
      throw new ParseException("Rule must have a type ('rule')", 0);
    }
    String rule = ruleDef.get("rule");

    if (!scannedRules.containsKey(rule)) {
      throw new ParseException("Unknown rule: '" + rule + "'", 0);
    }
    RuleDefinition rd = scannedRules.get(rule);

    ExcludeRule ruleInst;
    try {
      ruleInst = (ExcludeRule) rd.type.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      logger.error("Could not make instance for rule '" + rule + "' of " + rd.type, e);
      throw new ParseException("Could not instantiate rule '" + rule + "'", 0);
    }

    Set<String> requiredProperties = rd.properties.entrySet().stream()
        .filter(e -> e.getValue().annotation.required())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());

    for (String propertyName : ruleDef.keySet()) {
      if ("rule".equals(propertyName)) {
        continue;
      }

      if (!rd.properties.containsKey(propertyName)) {
        throw new ParseException("Invalid property for rule '" + rule + "': '" + propertyName + "'", 0);
      }

      try {
        rd.properties.get(propertyName).method.invoke(ruleInst, ruleDef.get(propertyName));
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.error("Attempted to set property '" + propertyName + "' of rule '" + rule + "'", e);
        throw new ParseException("Could not set property for rule '" + rule + "': '"+ propertyName + "'", 0);
      }
      requiredProperties.remove(propertyName);
    }

    if (!requiredProperties.isEmpty()) {
      throw new ParseException("Missing properties for rule '" + rule + "': " + requiredProperties.stream().collect(Collectors.joining(", ")), 0);
    }

    return ruleInst;
  }

  private class RuleDefinition {
    Class<?> type;
    Map<String, PropertyDefinition> properties = new HashMap<>();

    private class PropertyDefinition {
      Method method;
      RuleParam annotation;
    }
  }

  private static class JsonConf {
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
      return "JsonConf{" +
          "directories=" + directories +
          ", globalRules=" + globalRules +
          '}';
    }

    private static class DirectoryConf {
      private String directory;
      private List<Map<String, String>> rules;
      private RetentionPolicy retentionPolicy;

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

      public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
      }

      public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
      }

      @Override
      public String toString() {
        return "DirectoryConf{" +
            "directory='" + directory + '\'' +
            ", rules=" + rules +
            ", retentionPolicy=" + retentionPolicy +
            '}';
      }
    }
  }
}
