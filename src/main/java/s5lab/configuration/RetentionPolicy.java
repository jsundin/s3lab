package s5lab.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RetentionPolicy {
  IGNORE("ignore"),
  FAIL("fail"),
  FORGET("forget"),
  DELETE_BACKUPS("delete-backups");

  private final String value;

  RetentionPolicy(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
