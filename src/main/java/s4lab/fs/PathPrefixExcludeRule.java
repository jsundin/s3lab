package s4lab.fs;

import java.io.File;

public class PathPrefixExcludeRule implements ExcludeRule {
  private final String prefix;

  public PathPrefixExcludeRule(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean exclude(File f) {
    return f.toString().startsWith(prefix);
  }
}
