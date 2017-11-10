package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class VersioningConfiguration {
  private final int intervalInMinutes;
  private final VersioningStrategy deletedFilesStrategy;
  private final VersioningStrategy oldVersionsStrategy;

  @JsonCreator
  public VersioningConfiguration(
      @JsonProperty("interval") @JsonDeserialize(using = IntervalToMinutesDeserializer.class) int intervalInMinutes,
      @JsonProperty("deleted-files") VersioningStrategy deletedFilesStrategy,
      @JsonProperty("old-versions") VersioningStrategy oldVersionsStrategy) {
    this.intervalInMinutes = intervalInMinutes;
    this.deletedFilesStrategy = deletedFilesStrategy;
    this.oldVersionsStrategy = oldVersionsStrategy;
  }

  public int getIntervalInMinutes() {
    return intervalInMinutes;
  }

  public VersioningStrategy getDeletedFilesStrategy() {
    return deletedFilesStrategy;
  }

  public VersioningStrategy getOldVersionsStrategy() {
    return oldVersionsStrategy;
  }
}
