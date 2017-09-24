package s3lab.filemon;

import java.nio.file.*;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    Path p = FileSystems.getDefault().getPath("/home/jsundin/tmp/filemontest");
    Path p2 = FileSystems.getDefault().getPath("/home/jsundin/tmp/filemontest/subdir");
    try (WatchService ws = FileSystems.getDefault().newWatchService()) {
      WatchKey watchKey = p.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);

      while (true) {
        List<WatchEvent<?>> events = watchKey.pollEvents();
        if (!events.isEmpty()) {
          System.out.println(events);
        }
        Thread.sleep(100);
      }
    }
  }
}
