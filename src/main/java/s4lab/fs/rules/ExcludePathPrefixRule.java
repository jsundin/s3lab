package s4lab.fs.rules;

import java.io.File;

public class ExcludePathPrefixRule implements ExcludeRule {
  private final String prefix;

  public ExcludePathPrefixRule(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean exclude(File f) {
    return f.toString().startsWith(prefix);
  }
}
