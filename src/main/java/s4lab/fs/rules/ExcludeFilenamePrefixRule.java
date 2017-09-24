package s4lab.fs.rules;

import java.io.File;

public class ExcludeFilenamePrefixRule implements ExcludeRule {
  private final String prefix;

  public ExcludeFilenamePrefixRule(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean exclude(File f) {
    return f.getName().startsWith(prefix);
  }
}
