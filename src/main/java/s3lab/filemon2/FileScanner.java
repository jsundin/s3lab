package s3lab.filemon2;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static s3lab.Utils.longToLDT;

public class FileScanner {
  private List<Path> includedPaths = new ArrayList<>();
  private List<Path> excludedPaths = new ArrayList<>();
  private final BackupAgent agent;
  private final DbAdapter dbAdapter;

  public FileScanner(BackupAgent agent, DbAdapter dbAdapter) {
    this.agent = agent;
    this.dbAdapter = dbAdapter;
  }

  public void addInclude(String... includes) {
    includedPaths.addAll(Arrays.stream(includes).map(i -> Paths.get(i)).collect(Collectors.toList()));
  }

  public void addExclude(String... excludes) {
    excludedPaths.addAll(Arrays.stream(excludes).map(e -> Paths.get(e)).collect(Collectors.toList()));
  }

  private List<Path> computeEffectivePaths(List<Path> includes, List<Path> excludes) {
    List<Path> effective = new ArrayList<>();
    includes.forEach(p -> {
      try {
        effective.addAll(Files.walk(p)
                .filter(f -> f.toFile().isDirectory())
                .filter(f -> {
                  for (int i = 0; i < excludes.size(); i++) {
                    if (f.startsWith(excludes.get(i))) {
                      return false;
                    }
                  }
                  return true;
                })
                .collect(Collectors.toList()));
      } catch (IOException e) {
        throw new RuntimeException(e); // TODO: bättre hanterat?
      }
    });
    return effective;
  }

  private Map<URI, FileDefinition> findFiles(List<Path> paths) {
    Map<URI, FileDefinition> files = new HashMap<>();
    paths.forEach(p -> {
      try {
        files.putAll(Files.walk(p, 1)
                .filter(f -> f.toFile().isFile())
                .map(f -> {
                  FileDefinition fd = new FileDefinition();
                  fd.setFile(f);
                  fd.setLastModified(longToLDT(f.toFile().lastModified()));
                  return fd;
                })
                .collect(Collectors.toMap(k -> k.getFile().toUri(), v -> v)));
      } catch (IOException e) {
        e.printStackTrace();
      }
    });

    return files;
  }

  public void scan() throws SQLException {
    List<Path> effectivePaths = computeEffectivePaths(includedPaths, excludedPaths);
    Map<URI, FileDefinition> savedList = dbAdapter.findAllFiles();
    Map<URI, FileDefinition> currentList = findFiles(effectivePaths);

    // deletes:
    // om en fil som är sparad inte längre återfinns på filsystemet
    // OCH den inte är deletad sedan tidigare
    List<URI> deletes = savedList.entrySet().stream()
            .filter(e -> e.getValue().getDeletedAt() == null)
            .filter(e -> !currentList.keySet().contains(e.getKey()))
            .map(e -> e.getKey())
            .collect(Collectors.toList());

    // creates:
    // on en fil som finns på filsystemet inte återfinns bland de sparade
    // ELLER
    // om en fil som finns på filsystemet FINNS bland de sparade men är borttagen - räknas som ny!
    List<URI> creates = currentList.keySet().stream()
            .filter(uri -> {
              if (!savedList.keySet().contains(uri)) {
                return true;
              }
              return savedList.get(uri).getDeletedAt() != null;
            })
            .collect(Collectors.toList());

    // modifies:
    // alla filer som finns på både filsystemet och bland de sparade
    // OCH inte är borttagna (räknas som nya)
    // OCH
    //    har ändrat sig
    //    ELLER
    //    inte har pushats
    List<URI> modifies = currentList.entrySet().stream()
            .filter(e -> !deletes.contains(e.getKey()))
            .filter(e -> savedList.containsKey(e.getKey()))
            .filter(e -> {
              FileDefinition saved = savedList.get(e.getKey());
              if (saved.getDeletedAt() != null) {
                return false;
              }
              if (!e.getValue().getLastModified().equals(saved.getLastModified())) {
                return true;
              }
              if (saved.getLastPushed() == null) {
                return true;
              }
              return false;
            })
            .map(e -> e.getKey())
            .collect(Collectors.toList());


    agent.tagFile(BackupAgent.FileOperation.CREATE, creates);
    agent.tagFile(BackupAgent.FileOperation.MODIFY, modifies);
    agent.tagFile(BackupAgent.FileOperation.DELETE, deletes);
  }
}
