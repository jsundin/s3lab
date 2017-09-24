package s4lab.fs;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class SymlinkExcludeRule implements ExcludeRule {
  @Override
  public boolean exclude(File f) {
    try {
      return FileUtils.isSymlink(f);
    } catch (IOException e) {
      return true;
    }
  }
}
