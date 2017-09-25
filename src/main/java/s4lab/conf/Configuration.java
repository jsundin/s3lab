package s4lab.conf;

import s4lab.fs.DirectoryConfiguration;
import s4lab.fs.rules.ExcludeRule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author johdin
 * @since 2017-09-25
 */
public class Configuration {
  private List<DirectoryConfiguration> directoryConfigurations = new ArrayList<>();
  private List<ExcludeRule> globalExcludeRules = new ArrayList<>();

  public List<DirectoryConfiguration> getDirectoryConfigurations() {
    return directoryConfigurations;
  }

  public List<ExcludeRule> getGlobalExcludeRules() {
    return globalExcludeRules;
  }
}
