package s4lab.fs;

import java.io.File;

public class HiddenFileExcludeRule implements ExcludeRule {
  @Override
  public boolean exclude(File f) {
    return f.isHidden();
  }
}
