package s4lab.agent.backuptarget.localarchive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.agent.FileUploadJob;
import s4lab.agent.backuptarget.BackupSession;
import s4lab.agent.backuptarget.BackupTarget;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author johdin
 * @since 2017-10-06
 */
public class LocalArchiveBackupTargetNG implements BackupTarget {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static int threadIndex = 0;
  private final TarGzArchiverInstanceFactory tarGzArchiverInstanceFactory;

  public LocalArchiveBackupTargetNG(TarGzArchiverInstanceFactory tarGzArchiverInstanceFactory) {
    this.tarGzArchiverInstanceFactory = tarGzArchiverInstanceFactory;
  }

  @Override
  public BackupSession openSession() {
    LocalArchiveBackupTargetThread thread = new LocalArchiveBackupTargetThread(tarGzArchiverInstanceFactory.newInstance());
    thread.start();
    return thread;
  }

  @Override
  public void closeSession(BackupSession session) {
    if (session == null || !(session instanceof LocalArchiveBackupTargetThread)) {
      throw new IllegalStateException("Wrong session type: " + (session == null ? "(null)" : session.getClass()));
    }
    ((LocalArchiveBackupTargetThread) session).close();
    try {
      ((LocalArchiveBackupTargetThread) session).join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void handleJob(BackupSession session, FileUploadJob job) throws IOException {
    if (session == null || !(session instanceof LocalArchiveBackupTargetThread)) {
      throw new IllegalStateException("Wrong session type: " + (session == null ? "(null)" : session.getClass()));
    }
    ((LocalArchiveBackupTargetThread) session).addJob(job);
  }

  public interface TarGzArchiverInstanceFactory {
    TarGzArchiver newInstance();
  }

  private class LocalArchiveBackupTargetThread extends Thread implements BackupSession {
    private BlockingQueue<LocalArchiveBackupTargetJob> jobs = new LinkedBlockingQueue<>();
    private final TarGzArchiver archiver;

    public LocalArchiveBackupTargetThread(TarGzArchiver archiver) {
      super("LocalArchiveBackupTarget-" + (threadIndex++));
      this.archiver = archiver;
    }

    private void close() {
      jobs.add(new LocalArchiveBackupTargetJob(true));
    }

    private void addJob(FileUploadJob job) {
      jobs.add(new LocalArchiveBackupTargetJob(job));
    }

    @Override
    public void run() {
      do {
        try {
          LocalArchiveBackupTargetJob job = jobs.take();
          if (job.closed) {
            archiver.close();
            logger.info("Session closed");
            break;
          }
          try {
            if (job.fileUploadJob.isDeleted()) {
              archiver.addDeleteMarker(job.fileUploadJob.getFile(), job.fileUploadJob.getVersion());
            } else {
              archiver.addFile(job.fileUploadJob.getFile(), job.fileUploadJob.getVersion());
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        } catch (InterruptedException ignored) {}
      } while (true);
    }
  }

  private class LocalArchiveBackupTargetJob {
    private final FileUploadJob fileUploadJob;
    private final boolean closed;

    public LocalArchiveBackupTargetJob(FileUploadJob fileUploadJob) {
      this.fileUploadJob = fileUploadJob;
      this.closed = false;
    }

    public LocalArchiveBackupTargetJob(boolean closed) {
      this.fileUploadJob = null;
      this.closed = closed;
    }

    public FileUploadJob getFileUploadJob() {
      return fileUploadJob;
    }

    public boolean isClosed() {
      return closed;
    }
  }
}
