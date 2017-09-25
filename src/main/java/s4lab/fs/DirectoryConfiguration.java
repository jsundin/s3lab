package s4lab.fs;

import s4lab.fs.rules.ExcludeRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DirectoryConfiguration {
  private final String directory;
  private final List<ExcludeRule> excludeRules;

  public DirectoryConfiguration(String directory, ExcludeRule... excludeRules) {
    this.directory = directory;
    if (excludeRules != null && excludeRules.length > 0) {
      this.excludeRules = Collections.unmodifiableList(Arrays.asList(excludeRules));
    } else {
      this.excludeRules = Collections.unmodifiableList(Collections.emptyList());
    }
  }

  public String getDirectory() {
    return directory;
  }

  public List<ExcludeRule> getExcludeRules() {
    return excludeRules;
  }
}
