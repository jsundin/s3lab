package s5lab.configuration;

import java.io.File;

public class ExcludeHiddenFilesFileRule implements FileRule {
  @Override
  public boolean accept(File f) {
    return !f.isHidden();
  }
}
