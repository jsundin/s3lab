package ng3.agent;

import java.time.ZonedDateTime;
import java.util.List;

public interface VersioningReport {
  List<String> getErrors();
  List<String> getWarnings();
  ZonedDateTime getStartedAt();
  ZonedDateTime getFinishedAt();
}
