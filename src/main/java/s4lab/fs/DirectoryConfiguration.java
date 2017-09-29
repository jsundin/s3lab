package s4lab.fs;

import s4lab.conf.RetentionPolicy;
import s4lab.fs.rules.ExcludeRule;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DirectoryConfiguration {
  private final File directory;
  private final List<ExcludeRule> excludeRules;
  private RetentionPolicy retentionPolicy;

  public DirectoryConfiguration(File directory, ExcludeRule... excludeRules) {
    this.directory = directory;
    if (excludeRules != null && excludeRules.length > 0) {
      this.excludeRules = Collections.unmodifiableList(Arrays.asList(excludeRules));
    } else {
      this.excludeRules = Collections.unmodifiableList(Collections.emptyList());
    }
  }

  public RetentionPolicy getRetentionPolicy() {
    return retentionPolicy;
  }

  public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
    this.retentionPolicy = retentionPolicy;
  }

  public File getDirectory() {
    return directory;
  }

  public List<ExcludeRule> getExcludeRules() {
    return excludeRules;
  }
}
