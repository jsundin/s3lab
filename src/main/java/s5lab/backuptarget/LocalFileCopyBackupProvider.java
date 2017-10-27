package s5lab.backuptarget;

import com.fasterxml.jackson.annotation.JsonProperty;
import s5lab.configuration.ConfigurationException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LocalFileCopyBackupProvider implements BackupProvider {
  private String id;
  private File targetDirectory;
  private boolean createTargetIfNeeded;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public JobTargetConfiguration parseJobTargetConfiguration(Map<String, Object> configuration) throws ConfigurationException {
    LocalFileCopyBackupJobTargetConfiguration conf = new LocalFileCopyBackupJobTargetConfiguration();
    Map<String, Object> rawConf = new HashMap<>(configuration);

    if (rawConf.containsKey("subdir")) {
      conf.setSubdir((String) rawConf.get("subdir"));
      rawConf.remove("subdir");
    }

    if (!rawConf.isEmpty()) {
      throw new ConfigurationException("Unknown properties: " + rawConf.keySet());
    }
    return conf;
  }

  @Override
  public void start() {

  }

  @Override
  public void finish() {

  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isCreateTargetIfNeeded() {
    return createTargetIfNeeded;
  }

  @JsonProperty("create-if-needed")
  public void setCreateTargetIfNeeded(boolean createTargetIfNeeded) {
    this.createTargetIfNeeded = createTargetIfNeeded;
  }

  public File getTargetDirectory() {
    return targetDirectory;
  }

  @JsonProperty("target-directory")
  public void setTargetDirectory(File targetDirectory) {
    this.targetDirectory = targetDirectory;
  }

  public class LocalFileCopyBackupJobTargetConfiguration implements JobTargetConfiguration {
    private String subdir;

    public String getSubdir() {
      return subdir;
    }

    public void setSubdir(String subdir) {
      this.subdir = subdir;
    }
  }
}
