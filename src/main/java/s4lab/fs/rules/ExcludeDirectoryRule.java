package s4lab.fs.rules;

import s4lab.conf.Rule;
import s4lab.conf.RuleParam;

import java.io.File;

@Rule("exclude-directory")
public class ExcludeDirectoryRule implements ExcludeRule {
  private File directory;

  public ExcludeDirectoryRule() {
  }

  public ExcludeDirectoryRule(File directory) {
    this.directory = directory;
  }

  public void setDirectory(File directory) {
    this.directory = directory;
  }

  @RuleParam(value = "directory", required = true)
  public void setDirectory(String directory) {
    setDirectory(new File(directory));
  }

  @Override
  public boolean exclude(File f) {
    do {
      if (f.equals(directory)) {
        return true;
      }
      f = f.getParentFile();
    } while (f != null);
    return false;
  }
}
