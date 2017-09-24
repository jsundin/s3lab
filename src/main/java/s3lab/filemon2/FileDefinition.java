package s3lab.filemon2;

import java.nio.file.Path;
import java.time.LocalDateTime;

class FileDefinition {
  private Path file;
  private LocalDateTime lastModified;
  private LocalDateTime lastPushed;
  private LocalDateTime deletedAt;
  private int version;

  public Path getFile() {
    return file;
  }

  public void setFile(Path file) {
    this.file = file;
  }

  public LocalDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(LocalDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public LocalDateTime getLastPushed() {
    return lastPushed;
  }

  public void setLastPushed(LocalDateTime lastPushed) {
    this.lastPushed = lastPushed;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return "FileDefinition{" +
            "file=" + file +
            ", lastModified=" + lastModified +
            ", lastPushed=" + lastPushed +
            ", deletedAt=" + deletedAt +
            ", version=" + version +
            '}';
  }
}
