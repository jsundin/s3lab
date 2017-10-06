package s4lab.agent.backuptarget;

import s4lab.agent.FileUploadJob;

import java.io.IOException;

/**
 * @author johdin
 * @since 2017-10-06
 */
public interface BackupTarget {
  BackupSession openSession();
  void closeSession(BackupSession session);
  void handleJob(BackupSession session, FileUploadJob job) throws IOException;
}
