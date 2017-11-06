package s5lab.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;

public class ExcludeLongFilenamesRule implements FileRule {
  private int maxLength;

  public int getMaxLength() {
    return maxLength;
  }

  @JsonProperty("max-length")
  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
  }

  @Override
  public boolean accept(File f) {
    return f.toString().length() <= maxLength;
  }
}
