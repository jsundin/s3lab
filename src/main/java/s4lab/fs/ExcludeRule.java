package s4lab.fs;

import java.io.File;

public interface ExcludeRule {
  boolean exclude(File f);
}
