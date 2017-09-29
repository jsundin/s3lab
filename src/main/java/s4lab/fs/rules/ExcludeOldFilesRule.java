package s4lab.fs.rules;

import s4lab.Utils;
import s4lab.conf.Rule;
import s4lab.conf.RuleParam;
import s4lab.conf.Settings;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;

@Rule("excludeOldFiles")
public class ExcludeOldFilesRule implements ExcludeRule {
  private ZonedDateTime cutoff;

  public ExcludeOldFilesRule() {
  }

  public ExcludeOldFilesRule(ZonedDateTime cutoff) {
    setCutoff(cutoff);
  }

  public ZonedDateTime getCutoff() {
    return cutoff;
  }

  @RuleParam("cutoff")
  public void setCutoff(String cutoff) throws ParseException {
    new SimpleDateFormat(Settings.TIMESTAMP_FORMAT).parse(cutoff);
  }

  public void setCutoff(ZonedDateTime cutoff) {
    this.cutoff = cutoff;
  }

  @Override
  public boolean exclude(File f) {
    if (!f.isFile() || cutoff == null) {
      return false;
    }
    ZonedDateTime lastModified = Utils.lastModified(f);
    return lastModified.isBefore(cutoff) || lastModified.equals(cutoff);
  }
}
