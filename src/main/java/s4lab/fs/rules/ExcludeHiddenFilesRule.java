package s4lab.fs.rules;

import java.io.File;

public class ExcludeHiddenFilesRule implements ExcludeRule {
  @Override
  public boolean exclude(File f) {
    return f.isHidden();
  }
}
