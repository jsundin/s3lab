package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;

public class DirectoryConfiguration {
  private final File directory;

  @JsonCreator
  public DirectoryConfiguration(
          @JsonProperty("directory") File directory) {
    this.directory = directory;
  }

  public File getDirectory() {
    return directory;
  }
}
