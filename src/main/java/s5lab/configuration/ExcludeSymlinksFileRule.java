package s5lab.configuration;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class ExcludeSymlinksFileRule implements FileRule {
  @Override
  public boolean accept(File f) {
    try {
      return !FileUtils.isSymlink(f);
    } catch (IOException e) {
      return false;
    }
  }
}
