package s5lab.configuration;

import java.io.File;

public class ExcludeDirectoryFileRule implements FileRule {
  private File directory;

  public File getDirectory() {
    return directory;
  }

  public void setDirectory(File directory) {
    this.directory = directory;
  }

  @Override
  public boolean accept(File f) {
    do {
      if (f.equals(directory)) {
        return false;
      }
      f = f.getParentFile();
    } while (f != null);
    return true;
  }
}
