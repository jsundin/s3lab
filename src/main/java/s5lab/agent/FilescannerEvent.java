package s5lab.agent;

import java.io.File;
import java.util.UUID;

public class FilescannerEvent {
  private final UUID jobId;
  private final File file;

  public FilescannerEvent(UUID jobId, File file) {
    this.jobId = jobId;
    this.file = file;
  }

  public UUID getJobId() {
    return jobId;
  }

  public File getFile() {
    return file;
  }
}
