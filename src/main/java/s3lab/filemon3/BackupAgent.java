package s3lab.filemon3;

import s3lab.Looper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static s3lab.Utils.getPath;
import static s3lab.Utils.longToLDT;

public class BackupAgent extends Thread {
  private final DbAdapter dbAdapter;
  private final Looper looper = new Looper();

  public BackupAgent(DbAdapter dbAdapter) {
    super("BackupAgent");
    this.dbAdapter = dbAdapter;
  }

  public void finish() {
    looper.finish();
  }

  public void touchFile(List<String> filenames) {
    looper.post(new FileOperationMessage(FileOperation.TOUCH, filenames));
  }

  public void retireFile(List<String> filenames) {
    looper.post(new FileOperationMessage(FileOperation.RETIRE, filenames));
  }

  public void cleanup() {
    looper.post(new CleanupMessage());
  }

  @Override
  public void run() {
    looper.loop((Looper.MessageHandler) rm -> {
      if (rm instanceof FileOperationMessage) {
        FileOperationMessage m = (FileOperationMessage) rm;
        switch (m.fileOperation) {
          case TOUCH:
            touchFiles(m.filenames);
            break;

          case RETIRE:
            retireFiles(m.filenames);
            break;

          default:
            throw new RuntimeException("Unknown message type: " + m.fileOperation);
        }
      } else if (rm instanceof CleanupMessage) {
        doCleanup();
      } else {
        throw new RuntimeException("Unknown message: " + rm.getClass());
      }
    });
  }

  private void touchFiles(List<String> filenames) {
    Map<String, FileDefinition> existingFiles;
    try {
       existingFiles = dbAdapter.findFilesByFilename(filenames);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can't deal with this now
    }

    List<FileDefinition> inserts = new ArrayList<>();

    for (String filename : filenames) {
      if (existingFiles.containsKey(filename)) {
        FileDefinition fd = existingFiles.get(filename);
        fd.setVersion(fd.getVersion() + 1);
        fd.setLastModified(longToLDT(fd.getFile().toFile().lastModified()));
        fd.setDeleted(false);
        inserts.add(fd);
      } else {
        FileDefinition fd = new FileDefinition();
        fd.setFile(getPath(filename));
        fd.setVersion(0);
        fd.setLastModified(longToLDT(fd.getFile().toFile().lastModified()));
        fd.setDeleted(false);
        inserts.add(fd);
      }
    }

    try {
      System.out.println("Inserts: " + inserts);
      dbAdapter.insertFiles(inserts);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can't deal with this now
    }
  }

  private void retireFiles(List<String> filenames) {
    Map<String, FileDefinition> existingFiles;
    try {
      existingFiles = dbAdapter.findFilesByFilename(filenames);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can't deal with this now
    }

    List<FileDefinition> inserts = new ArrayList<>();
    for (FileDefinition fd : existingFiles.values()) {
      fd.setVersion(fd.getVersion() + 1);
      fd.setDeleted(true);
      fd.setLastModified(LocalDateTime.now());
      inserts.add(fd);
    }

    try {
      System.out.println("Retires: " + inserts);
      dbAdapter.insertFiles(inserts);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can't deal with this now
    }
  }

  private void doCleanup() {
    List<FileVersion> oldDeletedFiles;
    List<FileVersion> retiredVersions;
    try {
      oldDeletedFiles = dbAdapter.findOldDeletedFiles(LocalDateTime.now().minusSeconds(1));
      retiredVersions = dbAdapter.findRetiredVersions(3);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can't deal with this now
    }

    int n_oldDeletedFiles = oldDeletedFiles.size();
    int n_retiredVersions = retiredVersions.size();

    retiredVersions.removeAll(oldDeletedFiles);
    oldDeletedFiles.addAll(retiredVersions);

    System.out.println("Removing a total of " + oldDeletedFiles.size() + " old versions (" + n_oldDeletedFiles + " deleted files and " + n_retiredVersions + " retired versions)");

    try {
      dbAdapter.deleteFiles(oldDeletedFiles);
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO: we can't deal with this now
    }
  }

  private enum FileOperation {
    TOUCH,
    RETIRE
  }

  private class FileOperationMessage implements Looper.Message {
    private final FileOperation fileOperation;
    private final List<String> filenames;

    public FileOperationMessage(FileOperation fileOperation, List<String> filenames) {
      this.fileOperation = fileOperation;
      this.filenames = filenames;
    }
  }

  private class CleanupMessage implements Looper.Message {
  }
}
