package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class VersioningStrategy {
  private final Strategy strategy;
  private final Integer deleteAfterMinutes = 0;
  private final Integer keepVersions = 0;

  @JsonCreator
  public VersioningStrategy(
    @JsonProperty("strategy") Strategy strategy
  ) {
    this.strategy = strategy;
  }
/*
  @JsonCreator
  public VersioningStrategy(
      @JsonProperty("strategy") Strategy strategy,
      @JsonProperty("delete-after") @JsonDeserialize(using = IntervalToMinutesDeserializer.class) Integer deleteAfterMinutes,
      @JsonProperty("keep-versions") Integer keepVersions) {
    this.strategy = strategy;
    switch (strategy) {
      case DELETE:
      case KEEP:
        this.deleteAfterMinutes = null;
        this.keepVersions = null;
        break;

      case DELETE_AFTER:
        if (deleteAfterMinutes == null) {
          throw new IllegalArgumentException("'delete-after' must be set");
        }
        this.deleteAfterMinutes = deleteAfterMinutes;
        this.keepVersions = null;
        break;

      case KEEP_VERSIONS:
        if (keepVersions == null) {
          throw new IllegalArgumentException("'keep-versions' must be set");
        }
        this.deleteAfterMinutes = null;
        this.keepVersions = keepVersions;
        break;

      default:
        throw new IllegalArgumentException("Unknown strategory: '" + strategy + "'");
    }
  }
*/

  public Strategy getStrategy() {
    return strategy;
  }

  public enum Strategy {
    DELETE("delete"),
    KEEP("keep"),
    DELETE_AFTER("delete-after"),
    KEEP_VERSIONS("keep-versions");

    private final String value;

    Strategy(String value) {
      this.value = value;
    }

    @JsonCreator
    public static Strategy fromValue(String value) {
      for (Strategy strategy : Strategy.values()) {
        if (strategy.value.equals(value)) {
          return strategy;
        }
      }
      throw new IllegalArgumentException("Invalid value '" + value + "'");
    }

    public String getValue() {
      return value;
    }
  }
}
