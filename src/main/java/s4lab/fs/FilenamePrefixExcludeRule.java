package s4lab.fs;

import java.io.File;

public class FilenamePrefixExcludeRule implements ExcludeRule {
  private final String prefix;

  public FilenamePrefixExcludeRule(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean exclude(File f) {
    return f.getName().startsWith(prefix);
  }
}
