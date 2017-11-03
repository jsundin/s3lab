package ng.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;

/**
 * @author johdin
 * @since 2017-11-03
 */
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
