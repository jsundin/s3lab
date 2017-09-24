package s4lab.fs.rules;

import java.io.File;

public interface ExcludeRule {
  boolean exclude(File f);
}
