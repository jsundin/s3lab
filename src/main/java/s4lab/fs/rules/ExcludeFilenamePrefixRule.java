package s4lab.fs.rules;

import s4lab.conf.Rule;
import s4lab.conf.RuleParam;

import java.io.File;

@Rule("excludeFilenamePrefix")
public class ExcludeFilenamePrefixRule implements ExcludeRule {
  private String prefix;

  public String getPrefix() {
    return prefix;
  }

  @RuleParam(value = "prefix", required = true)
  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean exclude(File f) {
    return f.getName().startsWith(prefix);
  }
}
