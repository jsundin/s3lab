package s4lab.fs.rules;

import s4lab.fs.rules.ExcludeRule;

import java.io.File;
import java.time.LocalDateTime;

import static s3lab.Utils.longToLDT;

public class ExcludeOldFilesRule implements ExcludeRule {
  private final LocalDateTime cutoff;

  public ExcludeOldFilesRule(LocalDateTime cutoff) {
    this.cutoff = cutoff;
  }

  @Override
  public boolean exclude(File f) {
    if (!f.isFile() || cutoff == null) {
      return false;
    }
    LocalDateTime lastModified = longToLDT(f.lastModified());
    return lastModified.isBefore(cutoff) || lastModified.equals(cutoff);
  }
}
