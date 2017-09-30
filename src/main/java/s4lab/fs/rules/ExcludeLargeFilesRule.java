package s4lab.fs.rules;

import s4lab.conf.Rule;
import s4lab.conf.RuleParam;

import java.io.File;

@Rule("exclude-large-files")
public class ExcludeLargeFilesRule implements ExcludeRule {
  private long maxSizeInBytes;

  public ExcludeLargeFilesRule() {
  }

  public ExcludeLargeFilesRule(long maxSizeInBytes) {
    this.maxSizeInBytes = maxSizeInBytes;
  }

  @RuleParam(value = "max-size", required = true)
  public void setMaxSize(String maxSize) {
    char lastChar = maxSize.charAt(maxSize.length() - 1);
    if (Character.isLetter(lastChar)) {
      lastChar = Character.toLowerCase(lastChar);
      long v = Long.parseLong(maxSize.substring(0, maxSize.length() - 2));
      switch (lastChar) {
        case 'k':
          maxSizeInBytes = v * 1024;
          break;

        case 'm':
          maxSizeInBytes = v * (1024 * 1024);
          break;

        case 'g':
          maxSizeInBytes = v * (1024 * 1024 * 1024);
          break;

        default:
          throw new IllegalArgumentException("Could not parse filesize '" + maxSize + "'");
      }
    } else {
      maxSizeInBytes = Long.parseLong(maxSize);
    }
  }

  public void setMaxSizeInBytes(long maxSizeInBytes) {
    this.maxSizeInBytes = maxSizeInBytes;
  }

  @Override
  public boolean exclude(File f) {
    return f.length() > maxSizeInBytes;
  }
}
