package s4lab.conf;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author johdin
 * @since 2017-09-25
 */
public enum RetentionPolicy {
  IGNORE("ignore"),
  CLEAR_LOCAL("clear-local"),
  CLEAR_EVERYTHING("clear-everything"),
  FAIL("fail");

  private final String value;

  RetentionPolicy(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
