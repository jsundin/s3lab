package ng3.conf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class VersioningConfiguration {
  private final VersioningStrategy deletedFilesStrategy;
  private final VersioningStrategy oldVersionsStrategy;

  @JsonCreator
  public VersioningConfiguration(
      @JsonProperty("deleted-files") VersioningStrategy deletedFilesStrategy,
      @JsonProperty("old-versions") VersioningStrategy oldVersionsStrategy) {
    this.deletedFilesStrategy = deletedFilesStrategy;
    this.oldVersionsStrategy = oldVersionsStrategy;
  }

  public VersioningStrategy getDeletedFilesStrategy() {
    return deletedFilesStrategy;
  }

  public VersioningStrategy getOldVersionsStrategy() {
    return oldVersionsStrategy;
  }
}
