package s4lab.fs.rules;

import s4lab.conf.Rule;

import java.io.File;

@Rule("excludeHiddenFiles")
public class ExcludeHiddenFilesRule implements ExcludeRule {
  @Override
  public boolean exclude(File f) {
    return f.isHidden();
  }
}
