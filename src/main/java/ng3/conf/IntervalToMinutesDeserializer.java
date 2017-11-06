package ng3.conf;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntervalToMinutesDeserializer extends JsonDeserializer<Integer> {
  private final static Pattern pattern = Pattern.compile("(?<num>[0-9]+)(?<classifier>[mhdwMy])");

  @Override
  public Integer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    String interval = jsonParser.getText();
    Matcher m = pattern.matcher(interval);
    if (!m.matches()) {
      throw new IOException("Invalid value for interval: '" + interval + "'");
    }

    int num = Integer.parseInt(m.group("num"));
    String classifier = m.group("classifier");

    switch (classifier) {
      case "m":
        return num;

      case "h":
        return num * 60;

      case "d":
        return num * 60 * 24;

      case "w":
        return num * 60 * 24* 7;

      case "M":
        return num * 60 * 24 * 30;

      case "y":
        return num * 60 * 24 * 365;

      default:
        throw new IOException("Unknown classifier in interval: '" + interval + "'");
    }
  }
}
