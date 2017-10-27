package s5lab.configuration;

import java.io.File;

public class ExcludeFilenamePrefixFileRule implements FileRule {
  private String prefix;

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean accept(File f) {
    return !f.getName().startsWith(prefix);
  }
}
