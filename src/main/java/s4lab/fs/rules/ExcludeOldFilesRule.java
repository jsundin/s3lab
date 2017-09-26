package s4lab.fs.rules;

import s4lab.conf.Rule;
import s4lab.conf.RuleParam;
import s4lab.conf.Settings;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;

import static s3lab.Utils.longToLDT;

@Rule("excludeOldFiles")
public class ExcludeOldFilesRule implements ExcludeRule {
  private LocalDateTime cutoff;

  public ExcludeOldFilesRule() {
  }

  public ExcludeOldFilesRule(LocalDateTime cutoff) {
    this.cutoff = cutoff;
  }

  public LocalDateTime getCutoff() {
    return cutoff;
  }

  @RuleParam("cutoff")
  public void setCutoff(String cutoff) throws ParseException {
    new SimpleDateFormat(Settings.TIMESTAMP_FORMAT).parse(cutoff);
  }

  public void setCutoff(LocalDateTime cutoff) {
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
