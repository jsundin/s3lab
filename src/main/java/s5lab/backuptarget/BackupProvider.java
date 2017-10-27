package s5lab.backuptarget;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import s5lab.configuration.ConfigurationException;

import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "provider")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LocalFileCopyBackupProvider.class, name = "local-file-copy")
})
public interface BackupProvider {
  String getId();
  JobTargetConfiguration parseJobTargetConfiguration(Map<String, Object> configuration) throws ConfigurationException;

  void start();
  void finish();
}
