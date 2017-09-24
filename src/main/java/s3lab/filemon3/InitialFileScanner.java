package s3lab.filemon3;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static s3lab.Utils.getFilename;
import static s3lab.Utils.longToLDT;

public class InitialFileScanner {
  private final DbAdapter dbAdapter;
  private final BackupAgent backupAgent;

  public InitialFileScanner(DbAdapter dbAdapter, BackupAgent backupAgent) {
    this.dbAdapter = dbAdapter;
    this.backupAgent = backupAgent;
  }

  public void scan(List<Path> includes, List<Path> excludes) throws SQLException {
    List<Path> effectivePaths = computeEffectivePaths(includes, excludes);

    Map<String, FileDefinition> physicalFiles = findPhysicalFiles(effectivePaths);
    Map<String, FileDefinition> dbFiles = dbAdapter.findAllFiles();

    // touches:
    // filer som inte finns i databasen men på filsystemet
    // ELLER
    // filer som finns i databasen OCH på filsystemet men har modifierats
    List<String> brandNewFiles = physicalFiles.entrySet().stream()
            .filter(e -> !dbFiles.containsKey(e.getKey()))
            .map(e -> e.getKey())
            .collect(Collectors.toList());

    List<String> modifiedFiles = physicalFiles.entrySet().stream()
            .filter(e -> dbFiles.containsKey(e.getKey()))
            .filter(e -> !dbFiles.get(e.getKey()).getLastModified().equals(longToLDT(e.getValue().getFile().toFile().lastModified())))
            .map(e -> e.getKey())
            .collect(Collectors.toList());

    // retires:
    // filer som finns i databasen men inte på filsystemet, som inte redan är dödsstämplade
    List<String> retireFiles = dbFiles.entrySet().stream()
            .filter(e -> !physicalFiles.containsKey(e.getKey()))
            .filter(e -> !e.getValue().isDeleted())
            .map(e -> e.getKey())
            .collect(Collectors.toList());

    backupAgent.touchFile(brandNewFiles);
    backupAgent.touchFile(modifiedFiles);
    backupAgent.retireFile(retireFiles);
  }

  private Map<String, FileDefinition> findPhysicalFiles(List<Path> paths) {
    Map<String, FileDefinition> files = new HashMap<>();
    paths.forEach(p -> {
      try {
        files.putAll(Files.walk(p, 1)
                .filter(f -> f.toFile().isFile())
                .map(f -> {
                  FileDefinition fd = new FileDefinition();
                  fd.setFile(f);
                  return fd;
                })
                .collect(Collectors.toMap(k -> getFilename(k.getFile()), v -> v)));
      } catch (IOException e) {
        throw new RuntimeException(e); // TODO: bättre hanterat?
      }
    });

    return files;
  }

  private List<Path> computeEffectivePaths(List<Path> includes, List<Path> excludes) {
    List<Path> effective = new ArrayList<>();
    includes.forEach(p -> {
      try {
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (!dir.toFile().isDirectory()) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            for (Path exclude : excludes) {
              if (dir.startsWith(exclude)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
            }
            return super.preVisitDirectory(dir, attrs);
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            System.out.println(">> Skipping path: " + file + " (" + exc.getMessage() + ")");
            return FileVisitResult.SKIP_SUBTREE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            effective.add(dir);
            return super.postVisitDirectory(dir, exc);
          }
        });
      } catch (IOException e) {
        throw new RuntimeException(e); // TODO: bättre hanterat?
      }
    });

    return effective;
  }
}
