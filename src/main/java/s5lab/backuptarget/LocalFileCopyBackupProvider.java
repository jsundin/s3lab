package s5lab.backuptarget;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.configuration.ConfigurationException;
import s5lab.configuration.JobConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LocalFileCopyBackupProvider implements BackupProvider {
  private final Logger logger = LoggerFactory.getLogger(getClass());
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
  public void enqueue(UUID jobId, File file, JobConfiguration jobConfiguration) {
    if (!(jobConfiguration.getTargetConfiguration() instanceof LocalFileCopyBackupJobTargetConfiguration)) {
      throw new RuntimeException(""); // TODO: fix
    }
    LocalFileCopyBackupJobTargetConfiguration tconf = (LocalFileCopyBackupJobTargetConfiguration) jobConfiguration.getTargetConfiguration();

    File target = targetDirectory;
    if (tconf.getSubdir() != null) {
      String filename = file.getName();
      String jcDir = jobConfiguration.getDirectory().toString();
      if (filename.startsWith(jcDir)) {
        filename = filename.substring(jcDir.length() + 1);
      }
      target = new File(target, tconf.getSubdir());
      target = new File(target, filename);
    } else {
      throw new RuntimeException(""); // TODO: support för att använda HELA sökvägen också
    }

    if (!target.getParentFile().exists()) {
      target.getParentFile().mkdirs();
    }

    target = getVersionedTarget(target);

    try (FileInputStream fis = new FileInputStream(file)) {
      try (FileOutputStream fos = new FileOutputStream(target)) {
        IOUtils.copy(fis, fos);
      }
    } catch (IOException e) {
      logger.error("Could not copy file '{}' to '{}'", file, target); // TODO: vad gör vi nu?
    }
  }

  private File getVersionedTarget(File target) {
    int n = 0;
    File tryTarget = target;
    while (tryTarget.exists()) {
      n++;
      tryTarget = new File(target.toString() + "," + n);
    }
    return tryTarget;
  }

  @Override
  public void start() {
    if (!targetDirectory.exists()) {
      if (!createTargetIfNeeded) {
        throw new RuntimeException(""); // TODO: fix
      }

      if (!targetDirectory.mkdirs()) {
        throw new RuntimeException(""); // TODO: fix
      }
    }
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
