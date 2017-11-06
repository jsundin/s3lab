package ng3.drivers;

import ng3.BackupDirectory;
import ng3.agent.BackupReportWriter;
import ng3.conf.Configuration;
import ng3.db.DbClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.DigestOutputStream;
import s4lab.agent.SecurityException;

import javax.crypto.CipherOutputStream;
import java.io.*;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * @author johdin
 * @since 2017-11-06
 */
public class ArchiveBackupDriver extends AbstractBackupDriver {
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  protected AbstractBackupSession openSession(DbClient dbClient, Configuration configuration, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
    ArchiveBackupSession session = new ArchiveBackupSession(dbClient, report, backupDirectories);
    return session;
  }

  private class ArchiveBackupSession extends AbstractBackupSession {
    private DigestOutputStream digestOutputStream;
    private CipherOutputStream cipherOutputStream;
    private GZIPOutputStream gzipOutputStream;
    private TarArchiveOutputStream tarOutputStream;
    private OutputStream outputStream;

    ArchiveBackupSession(DbClient dbClient, BackupReportWriter report, List<BackupDirectory> backupDirectories) {
      super(dbClient, report, backupDirectories);
    }

    @Override
    protected void init() {
      super.init();
      File target = new File("/tmp/backups/fil.tar");
      try {
        outputStream = digestOutputStream = new DigestOutputStream(new FileOutputStream(target));

/*
        if (encrypt) {
          outputStream = cipherOutputStream = CryptoUtils.getEncryptionOutputStream(key, salt, outputStream);
        }

        if (compress) {
          outputStream = gzipOutputStream = new GZIPOutputStream(outputStream);
        }
*/

        outputStream = tarOutputStream = new TarArchiveOutputStream(outputStream);
      } catch (SecurityException | IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void finish() {
      super.finish();
      IOUtils.closeQuietly(tarOutputStream, gzipOutputStream, cipherOutputStream, digestOutputStream);
    }

    @Override
    protected void handleFile(BackupFile backupFile) {
      logger.info(backupFile.file.toString());

      try {
        TarArchiveEntry entry = new TarArchiveEntry(backupFile.file);
        tarOutputStream.putArchiveEntry(entry);
        try (FileInputStream fis = new FileInputStream(backupFile.file)) {
          IOUtils.copy(fis, tarOutputStream);
        }
        tarOutputStream.closeArchiveEntry();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
