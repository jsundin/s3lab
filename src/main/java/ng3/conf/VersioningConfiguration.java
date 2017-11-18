package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ng3.Metadata;
import ng3.common.TimeUtilsNG;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public List<Integer> performVersioning(Map<Integer, Metadata.Meta> meta) {
      List<Integer> versionsToDelete = new ArrayList<>();
      ZonedDateTime now = ZonedDateTime.now();

      // remove all versions older than N minutes (e.g "remove versions older than 1w")
      if (getAgeInMinutes() != null && getRetainVersions() == null) {
        for (Integer version : meta.keySet()) {
          Metadata.Meta versionMeta = meta.get(version);
          ZonedDateTime lastModified = TimeUtilsNG.at(versionMeta.getLastModified()).to(ZoneId.systemDefault()).toZonedDateTime();
          if (lastModified.isBefore(now.minusMinutes(getAgeInMinutes()))) {
            versionsToDelete.add(version);
          }
        }
        return versionsToDelete;
      }

      ArrayList<Integer> versions = new ArrayList<>(meta.keySet());
      versions.sort((l, r) -> l < r ? 1 : l > r ? -1 : 0);

      // only keep the last N versions of a file (e.g "only keep the latest 7 versions of file")
      if (getRetainVersions() != null && getAgeInMinutes() == null) {
        for (int i = getRetainVersions(); i < versions.size(); i++) {
          versionsToDelete.add(versions.get(i));
        }
        return versionsToDelete;
      }

      // remove all versions older than N minutes BUT keep at least N versions (e.g "remove versions older than 1w but always keep the last 3 versions")
      if (getAgeInMinutes() != null && getRetainVersions() != null) {
        for (int i = getRetainVersions(); i < versions.size(); i++) {
          int version = versions.get(i);
          Metadata.Meta versionMeta = meta.get(version);
          ZonedDateTime lastModified = TimeUtilsNG.at(versionMeta.getLastModified()).to(ZoneId.systemDefault()).toZonedDateTime();
          if (lastModified.isBefore(now.minusMinutes(getAgeInMinutes()))) {
            versionsToDelete.add(version);
          }
        }
        return versionsToDelete;
      }

      throw new IllegalStateException("Could not determine file versioning strategy");
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

    public List<Integer> performVersioning(Map<Integer, Metadata.Meta> meta) {
      ArrayList<Integer> versions = new ArrayList<>(meta.keySet());
      versions.sort((l, r) -> l < r ? 1 : l > r ? -1 : 0);
      Metadata.Meta last = meta.get(versions.get(0));
      ZonedDateTime lastModified = TimeUtilsNG.at(last.getLastModified()).to(ZoneId.systemDefault()).toZonedDateTime();

      if (getStrategy() == Strategy.DELETE_FILE) {
        return performDeleteFileStrategy(versions, lastModified);
      } else if (getStrategy() == Strategy.DELETE_HISTORY) {
        return performDeleteHistoryStrategy(versions, lastModified, meta);
      } else {
        throw new IllegalArgumentException("Unknown strategy '" + getStrategy() + "'");
      }
    }

    private List<Integer> performDeleteFileStrategy(List<Integer> versions, ZonedDateTime lastModified) {
      if (getAfterMinutes() == null) {
        // delete entire file unconditionally
        return versions;
      }

      if (lastModified.isBefore(ZonedDateTime.now().minusMinutes(getAfterMinutes()))) {
        // delete entire file when it has been deleted for a certain time (e.g "permanently delete the file after one week")
        return versions;
      }

      return Collections.emptyList();
    }

    private List<Integer> performDeleteHistoryStrategy(List<Integer> versions, ZonedDateTime lastModified, Map<Integer, Metadata.Meta> meta) {
      ZonedDateTime now = ZonedDateTime.now();
      List<Integer> versionsToDelete = new ArrayList<>();

      // keep only one living version when the file has reached a certain age (e.g "only keep the latest living version after the file has been deleted for one week")
      if (getAfterMinutes() != null && getAgeInMinutes() == null && getRetainVersions() == null) {
        if (lastModified.isBefore(now.minusMinutes(getAfterMinutes()))) {
          boolean found = false;

          for (Integer version : versions) {
            if (!found && !meta.get(version).getDeleted()) {
              found = true;
              continue;
            }

            if (found) {
              versionsToDelete.add(version);
            }
          }
        }
        return versionsToDelete;
      }

      // remove all versions older than "age" if the file has reached a certain age (e.g "delete ALL versions older than 1y after the file has been deleted for one week")
      if (getAfterMinutes() != null && getAgeInMinutes() != null && getRetainVersions() == null) {
        if (lastModified.isBefore(now.minusMinutes(getAfterMinutes()))) {
          for (Integer version : versions) {
            ZonedDateTime versionLastModified = TimeUtilsNG.at(meta.get(version).getLastModified()).to(ZoneId.systemDefault()).toZonedDateTime();
            if (versionLastModified.isBefore(now.minusMinutes(getAgeInMinutes()))) {
              versionsToDelete.add(version);
            }
          }
        }
        return versionsToDelete;
      }

      // only keep the latest N versions
      if (getRetainVersions() != null && getAfterMinutes() == null && getAgeInMinutes() == null) {
        for (int i = getRetainVersions(); i < versions.size(); i++) {
          versionsToDelete.add(versions.get(i));
        }
        return versionsToDelete;
      }

      // only keep the latest N versions when the file has reached a certain age
      if (getAgeInMinutes() != null && getRetainVersions() != null && getAgeInMinutes() == null) {
        if (lastModified.isBefore(now.minusMinutes(getAgeInMinutes()))) {
          for (int i = getRetainVersions(); i < versions.size(); i++) {
            versionsToDelete.add(versions.get(i));
          }
        }
        return versionsToDelete;
      }

      throw new IllegalStateException("Could not determine file delete strategy");
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
