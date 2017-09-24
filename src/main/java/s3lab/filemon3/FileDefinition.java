package s3lab.filemon3;

import java.nio.file.Path;
import java.time.LocalDateTime;

public class FileDefinition {
  private Path file;
  private int version;
  private LocalDateTime lastModified;
  private boolean deleted;

  public Path getFile() {
    return file;
  }

  public void setFile(Path file) {
    this.file = file;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public LocalDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(LocalDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  @Override
  public String toString() {
    return "FileDefinition{" +
            "file=" + file +
            ", version=" + version +
            ", lastModified=" + lastModified +
            ", deleted=" + deleted +
            '}';
  }
}
