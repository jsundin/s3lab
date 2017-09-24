package s4lab.fs.rules;

import java.io.File;

public class ExcludePathSuffixRule implements ExcludeRule {
  private final String suffix;

  public ExcludePathSuffixRule(String suffix) {
    this.suffix = suffix;
  }

  @Override
  public boolean exclude(File f) {
    return f.toString().endsWith(suffix);
  }
}
