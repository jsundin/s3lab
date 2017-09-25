package s4lab.conf;

import s4lab.fs.DirectoryConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author johdin
 * @since 2017-09-25
 */
public class Configuration {
  private List<DirectoryConfiguration> directoryConfigurations = new ArrayList<>();

  public List<DirectoryConfiguration> getDirectoryConfigurations() {
    return directoryConfigurations;
  }
}
