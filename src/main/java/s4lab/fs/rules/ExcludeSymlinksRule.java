package s4lab.fs.rules;

import org.apache.commons.io.FileUtils;
import s4lab.conf.Rule;

import java.io.File;
import java.io.IOException;

@Rule("excludeSymlinks")
public class ExcludeSymlinksRule implements ExcludeRule {
  @Override
  public boolean exclude(File f) {
    try {
      return FileUtils.isSymlink(f);
    } catch (IOException e) {
      return true;
    }
  }
}
