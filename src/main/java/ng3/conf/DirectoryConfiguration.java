package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import s5lab.configuration.FileRule;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class DirectoryConfiguration {
  private final File directory;
  private final List<FileRule> rules;

  @JsonCreator
  public DirectoryConfiguration(
          @JsonProperty("directory") File directory,
          @JsonProperty("rules") List<FileRule> rules) {
    this.directory = directory;
    this.rules = Collections.unmodifiableList(rules);
  }

  public File getDirectory() {
    return directory;
  }

  public List<FileRule> getRules() {
    return rules;
  }
}
