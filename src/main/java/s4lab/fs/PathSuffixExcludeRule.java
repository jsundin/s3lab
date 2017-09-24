package s4lab.fs;

import java.io.File;

public class PathSuffixExcludeRule implements ExcludeRule {
  private final String suffix;

  public PathSuffixExcludeRule(String suffix) {
    this.suffix = suffix;
  }

  @Override
  public boolean exclude(File f) {
    return f.toString().endsWith(suffix);
  }
}
