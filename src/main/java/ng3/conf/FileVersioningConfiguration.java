package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ng3.Metadata;
import ng3.common.TimeUtilsNG;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileVersioningConfiguration {
  private final Strategy strategy;
  private final Integer ageInMinutes;
  private final Integer retainVersions;

  @JsonCreator
  public FileVersioningConfiguration(
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
