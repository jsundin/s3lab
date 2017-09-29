package s4lab;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;

public class Utils {
  public static ZonedDateTime lastModified(File file) {
    try {
      Object _fileTime = Files.getAttribute(file.toPath(), "unix:ctime");
      if (_fileTime instanceof FileTime) {
        FileTime fileTime = (FileTime) _fileTime;
        ZonedDateTime zdt = TimeUtils.at(fileTime).toZonedDateTime();
        System.out.println("Checking ctime for " + file + ": " + zdt);
        return zdt;
      }
      throw new RuntimeException("Files.getAttribute() did not return a FileTime as expected: " + _fileTime.getClass());
    } catch (IllegalArgumentException | UnsupportedOperationException e) { // unix:ctime not available on this system
      return TimeUtils.at(file.lastModified()).toZonedDateTime();
    } catch (IOException e) {
      throw new RuntimeException("Cannot read lastModified for '" + file + "'", e);
    }
  }
}
