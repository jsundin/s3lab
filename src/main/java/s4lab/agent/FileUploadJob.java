package s4lab.agent;

import java.io.File;
import java.util.UUID;

public class FileUploadJob {
  private final UUID fileId;
  private final int version;
  private final File file;
  private final boolean deleted;

  public FileUploadJob(UUID fileId, int version, File file, boolean deleted) {
    this.fileId = fileId;
    this.version = version;
    this.file = file;
    this.deleted = deleted;
  }

  public UUID getFileId() {
    return fileId;
  }

  public int getVersion() {
    return version;
  }

  public File getFile() {
    return file;
  }

  public boolean isDeleted() {
    return deleted;
  }
}
