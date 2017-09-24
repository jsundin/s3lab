package s3lab.filemon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Filemon {


  /*public void start(List<Path> paths, List<Path> exclude) throws IOException {
    WatchService watcher = FileSystems.getDefault().newWatchService();
    Map<WatchKey, Path> watchKeys = new HashMap<>();

    paths.forEach(p -> {
      try {
        List<Path> included = Files.walk(p)
                .filter(lp -> lp.toFile().isDirectory())
                .filter(lp -> {
                  for (int i = 0; i < exclude.size(); i++) {
                    if (lp.startsWith(exclude.get(i))) {
                      return false;
                    }
                  }
                  return true;
                })
                .collect(Collectors.toList());

        included.forEach(ip -> {
          try {
            WatchKey watchKey = ip.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(watchKey, ip);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    for (;;) {
      WatchKey key;
      try {
        key = watcher.take();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      Path path = watchKeys.get(key);
      if (path == null) {
        System.err.println("WatchKey not recognized: " + key);
        continue;
      }

      for (WatchEvent<?> _event : key.pollEvents()) {
        WatchEvent<Path> event = (WatchEvent<Path>) _event;
        Path context = event.context();
        WatchEvent.Kind<Path> kind = event.kind();
        System.out.println("  " + event + ": " + kind + ":" + context);
      }
      key.reset();
    }
  }*/

  private WatchService watcher;
  private Map<WatchKey, Path> watchKeys;

  public void start() {

  }

  public void stop() {

  }

  public void addIncludes(List<Path> paths) {

  }

  public void addExcludes(List<Path> paths) {

  }

  private List<Path> getEffectivePaths() {
    return new ArrayList<>();
  }

  public static void main(String[] args) throws Exception {
    Filemon fm = new Filemon();
    FileSystem fs = FileSystems.getDefault();
/*
    fm.start(
            Arrays.asList(fs.getPath("/home/jsundin/tmp/filemontest")),
            Arrays.asList(fs.getPath("/home/jsundin/tmp/filemontest/exclude"))
    );
*/
  }
}
