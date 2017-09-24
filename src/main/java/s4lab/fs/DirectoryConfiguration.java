package s4lab.fs;

public class DirectoryConfiguration {
  private final String directory;
  private final ExcludeRule[] excludeRules;

  public DirectoryConfiguration(String directory, ExcludeRule... excludeRules) {
    this.directory = directory;
    this.excludeRules = excludeRules;
  }

  public String getDirectory() {
    return directory;
  }

  public ExcludeRule[] getExcludeRules() {
    return excludeRules;
  }
}
