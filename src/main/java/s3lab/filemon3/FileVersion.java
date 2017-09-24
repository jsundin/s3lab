package s3lab.filemon3;

public class FileVersion {
  private final String filename;
  private final int version;

  public FileVersion(String filename, int version) {
    this.filename = filename;
    this.version = version;
  }

  public String getFilename() {
    return filename;
  }

  public int getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileVersion that = (FileVersion) o;

    if (version != that.version) return false;
    return filename != null ? filename.equals(that.filename) : that.filename == null;
  }

  @Override
  public int hashCode() {
    int result = filename != null ? filename.hashCode() : 0;
    result = 31 * result + version;
    return result;
  }

  @Override
  public String toString() {
    return "FileVersion{" +
            "filename='" + filename + '\'' +
            ", version=" + version +
            '}';
  }
}
