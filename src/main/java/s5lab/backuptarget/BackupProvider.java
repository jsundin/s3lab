package s5lab.backuptarget;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import s5lab.configuration.ConfigurationException;
import s5lab.configuration.JobConfiguration;

import java.io.File;
import java.util.Map;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "provider")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LocalFileCopyBackupProvider.class, name = "local-file-copy")
})
public interface BackupProvider {
  String getId();
  JobTargetConfiguration parseJobTargetConfiguration(Map<String, Object> configuration) throws ConfigurationException;
  void enqueue(UUID jobId, File file, JobConfiguration jobConfiguration);

  void start();
  void finish();
}
