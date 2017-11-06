package ng3.conf;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SizeToBytesDeserializer extends JsonDeserializer<Long> {
  private static final Pattern pattern = Pattern.compile("(?<num>[0-9]+)(?<classifier>[kmgKMG]?)");

  @Override
  public Long deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    String size = jsonParser.getText();
    Matcher m = pattern.matcher(size);
    if (!m.matches()) {
      throw new IOException("Invalid value for size: '" + size + "'");
    }

    long num = Long.parseLong(m.group("num"));
    String classifier = m.group("classifier");

    switch (classifier.toLowerCase()) {
      case "":
        return num;

      case "k":
        return num * 1024;

      case "m":
        return num * 1024 * 1024;

      case "g":
        return num * 1024 * 1024 * 1024;

      default:
        throw new IOException("Unknown classifier for size: '" + size + "'");
    }
  }
}
