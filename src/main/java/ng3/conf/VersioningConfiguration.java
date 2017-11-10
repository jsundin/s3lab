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

  @JsonCreator
  public VersioningConfiguration(
      @JsonProperty("interval") @JsonDeserialize(using = IntervalToMinutesDeserializer.class) int intervalInMinutes) {
    this.intervalInMinutes = intervalInMinutes;
  }

  public int getIntervalInMinutes() {
    return intervalInMinutes;
  }
}
