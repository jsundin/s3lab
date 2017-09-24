package s3lab.filemon2;

import s3lab.Looper;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static s3lab.Utils.longToLDT;

public class BackupAgent extends Thread {
  private final Looper looper = new Looper();
  private final DbAdapter dbAdapter;

  public BackupAgent(DbAdapter dbAdapter) {
    super("BackupAgent");
    this.dbAdapter = dbAdapter;
  }

  public void tagFile(FileOperation operation, List<URI> uris) {
    looper.post(new FileOperationMessage(operation, uris));
  }

  public void housekeeping() {
    looper.post(new HousekeepingMessage());
  }

  public void finish() {
    looper.finish();
  }

  @Override
  public void run() {
    looper.loop((Looper.MessageHandler) rawMessage -> {
      if (rawMessage instanceof HousekeepingMessage) {
        doHousekeeping();
      } else if (rawMessage instanceof FileOperationMessage) {
        FileOperationMessage m = (FileOperationMessage) rawMessage;
        System.out.println(m.fileOperation + ": " + m.uris);

        switch (m.fileOperation) {
          case CREATE:
            createFiles(m.uris);
            break;

          case MODIFY:
            modifyFiles(m.uris);
            break;

          case DELETE:
            deleteFiles(m.uris);
            break;

          default:
            throw new RuntimeException("Unknown file operation: " + m.fileOperation);
        }
      } else {
        throw new RuntimeException("Invalid message type: " + rawMessage.getClass());
      }
    });
  }

  private void doHousekeeping() {
    List<FileDefinition> filesToDelete;
    try {
      filesToDelete = dbAdapter.findOldDeletedFiles(LocalDateTime.now().minusSeconds(1));
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can probably handle this better, yes?
    }

    System.out.println("Files to delete: " + filesToDelete);
  }

  private void createFiles(List<URI> uris) {
    Map<URI, FileDefinition> foundFiles;
    try {
      foundFiles = dbAdapter.findFiles(uris).stream()
              .collect(Collectors.toMap(k -> k.getFile().toUri(), v -> v));
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can probably handle this better, yes?
    }

    modifyFiles(new ArrayList<>(foundFiles.keySet()));
    List<URI> actualCreated = uris.stream()
            .filter(uri -> !foundFiles.containsKey(uri))
            .collect(Collectors.toList());
    List<FileDefinition> fileDefinitions = new ArrayList<>();

    for (URI uri : actualCreated) {
      Path file = Paths.get(uri);
      // gör en massa backupigt här
      LocalDateTime lastPushed = LocalDateTime.now(); // det här ska vara tidpunkten när filens upload STARTADE
      LocalDateTime lastModified = longToLDT(file.toFile().lastModified()); // det här ska vara filens tidpunkt när vi gjorde backupen

      FileDefinition fd = new FileDefinition();
      fd.setFile(file);
      fd.setLastPushed(lastPushed);
      fd.setLastModified(lastModified);
      fileDefinitions.add(fd);
    }

    try {
      dbAdapter.addFiles(fileDefinitions);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can probably handle this better, yes?
    }
  }

  private void modifyFiles(List<URI> uris) {
    List<FileDefinition> fileDefinitions = new ArrayList<>();
    Map<URI, FileDefinition> foundFiles;
    try {
      foundFiles = dbAdapter.findFiles(uris).stream()
              .collect(Collectors.toMap(k -> k.getFile().toUri(), v -> v));
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can probably handle this better, yes?
    }

    for (int i = 0; i < uris.size(); i++) {
      Path file = Paths.get(uris.get(i));

      // gör en massa backupigt här
      LocalDateTime lastPushed = LocalDateTime.now(); // det här ska vara tidpunkten när filens upload STARTADE
      LocalDateTime lastModified = longToLDT(file.toFile().lastModified()); // det här ska vara filens tidpunkt när vi gjorde backupen

      FileDefinition fd = new FileDefinition();
      fd.setFile(file);
      fd.setLastPushed(lastPushed);
      fd.setLastModified(lastModified);
      fd.setDeletedAt(null);
      if (foundFiles.containsKey(file.toUri())) {
        fd.setVersion(foundFiles.get(file.toUri()).getVersion() + 1);
      }

      fileDefinitions.add(fd);
    }

    try {
      dbAdapter.updateFiles(fileDefinitions);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can probably handle this better, yes?
    }
  }

  private void deleteFiles(List<URI> uris) {
    LocalDateTime now = LocalDateTime.now();
    List<FileDefinition> foundFiles;
    try {
      foundFiles = dbAdapter.findFiles(uris);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can probably handle this better, yes?
    }

    foundFiles.forEach(f -> {
      f.setDeletedAt(now);
      f.setVersion(f.getVersion() + 1);
    });

    try {
      dbAdapter.updateFiles(foundFiles);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: better handling?
    }
  }

  public enum FileOperation {
    CREATE,
    MODIFY,
    DELETE
  }

  private class FileOperationMessage implements Looper.Message {
    private final FileOperation fileOperation;
    private final List<URI> uris;

    public FileOperationMessage(FileOperation fileOperation, List<URI> uris) {
      this.fileOperation = fileOperation;
      this.uris = uris;
    }
  }

  private class HousekeepingMessage implements Looper.Message {
  }
}
