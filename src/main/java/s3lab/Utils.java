package s3lab;

import s3lab.filemon3.FileDefinition;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Utils {
  public static LocalDateTime longToLDT(long epochMilli) {
    return Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

  public static String getFilename(Path path) {
    return path.toString();
  }

  public static String getFilename(FileDefinition fd) {
    return getFilename(fd.getFile());
  }

  public static Path getPath(String filename) {
    return Paths.get(filename);
  }
}
