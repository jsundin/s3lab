package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class VersioningConfiguration {
  private final FileStrategy fileStrategy;
  private final DeletedFileStrategy deletedFileStrategy;

  @JsonCreator
  public VersioningConfiguration(
      @JsonProperty("old-versions") FileStrategy fileStrategy,
      @JsonProperty("deleted") DeletedFileStrategy deletedFileStrategy) {
    this.fileStrategy = fileStrategy;
    this.deletedFileStrategy = deletedFileStrategy;
  }

  public FileStrategy getFileStrategy() {
    return fileStrategy;
  }

  public DeletedFileStrategy getDeletedFileStrategy() {
    return deletedFileStrategy;
  }

  public static class FileStrategy {
    private final Strategy strategy;
    private final Integer ageInMinutes;
    private final Integer retainVersions;

    @JsonCreator
    public FileStrategy(
        @JsonProperty("strategy") Strategy strategy,
        @JsonProperty("age") @JsonDeserialize(using = IntervalToMinutesDeserializer.class) Integer ageInMinutes,
        @JsonProperty("retain-versions") Integer retainVersions) {
      if (strategy != Strategy.DELETE_HISTORY) {
        throw new IllegalArgumentException("Illegal strategy: '" + strategy + "'");
      }

      if (ageInMinutes == null && retainVersions == null) {
        throw new IllegalArgumentException("Either 'age' or 'retain-versions' must be provided");
      }

      this.strategy = strategy;
      this.ageInMinutes = ageInMinutes;
      this.retainVersions = retainVersions;
    }

    public Strategy getStrategy() {
      return strategy;
    }

    public Integer getAgeInMinutes() {
      return ageInMinutes;
    }

    public Integer getRetainVersions() {
      return retainVersions;
    }

    public enum Strategy {
      DELETE_HISTORY("delete-history");

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
        throw new IllegalArgumentException("Invalid value for strategy: '" + value + "'");
      }
    }
  }

  public static class DeletedFileStrategy {
    private final Strategy strategy;
    private final Integer afterMinutes;
    private final Integer ageInMinutes;
    private final Integer retainVersions;

    @JsonCreator
    public DeletedFileStrategy(
        @JsonProperty("strategy") Strategy strategy,
        @JsonProperty("after") @JsonDeserialize(using = IntervalToMinutesDeserializer.class) Integer afterMinutes,
        @JsonProperty("age") @JsonDeserialize(using = IntervalToMinutesDeserializer.class) Integer ageInMinutes,
        @JsonProperty("retain-versions") Integer retainVersions) {
      switch (strategy) {
        case DELETE_FILE:
          if (ageInMinutes != null) {
            throw new IllegalArgumentException("'age' is not allowed for strategy '" + strategy + "'");
          }
          if (retainVersions != null) {
            throw new IllegalArgumentException("'retain-versions' is not allowed for strategy '" + strategy + "'");
          }
          break;

        case DELETE_HISTORY:
          if (ageInMinutes != null && afterMinutes == null) {
            throw new IllegalArgumentException("'age' requires 'after'");
          }
          if (ageInMinutes != null && retainVersions != null) {
            throw new IllegalArgumentException("'age' and 'retain-versions cannot be used together");
          }
          break;

        default:
          throw new IllegalArgumentException("Illegal strategy: '" + strategy + "'");
      }

      this.strategy = strategy;
      this.afterMinutes = afterMinutes;
      this.ageInMinutes = ageInMinutes;
      this.retainVersions = retainVersions;
    }

    public Strategy getStrategy() {
      return strategy;
    }

    public Integer getAfterMinutes() {
      return afterMinutes;
    }

    public Integer getAgeInMinutes() {
      return ageInMinutes;
    }

    public Integer getRetainVersions() {
      return retainVersions;
    }

    public enum Strategy {
      DELETE_FILE("delete-file"),
      DELETE_HISTORY("delete-history");
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
        throw new IllegalArgumentException("Invalid value for strategy: '" + value + "'");
      }
    }
  }
}
