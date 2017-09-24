package s3lab.file4;

import java.time.LocalDateTime;

public class VersionDefinition {
  private String id;
  private String fileId;
  private int version;
  private LocalDateTime modified;
  private boolean deleted;
}
