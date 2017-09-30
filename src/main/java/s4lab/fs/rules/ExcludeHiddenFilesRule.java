package s4lab.fs.rules;

import s4lab.conf.Rule;

import java.io.File;

@Rule("exclude-hidden-files")
public class ExcludeHiddenFilesRule implements ExcludeRule {
  @Override
  public boolean exclude(File f) {
    return f.isHidden();
  }
}
