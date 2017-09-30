package s4lab.fs.rules;

import s4lab.conf.Rule;
import s4lab.conf.RuleParam;

import java.io.File;

@Rule("exclude-path-suffix")
public class ExcludePathSuffixRule implements ExcludeRule {
  private String suffix;

  public String getSuffix() {
    return suffix;
  }

  @RuleParam(value = "suffix", required = true)
  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  @Override
  public boolean exclude(File f) {
    return f.toString().endsWith(suffix);
  }
}
