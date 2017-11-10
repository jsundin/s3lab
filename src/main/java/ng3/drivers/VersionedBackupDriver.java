package ng3.drivers;

import ng3.BackupDirectory;
import ng3.conf.Configuration;
import ng3.db.DbClient;

/**
 * @author johdin
 * @since 2017-11-10
 */
public interface VersionedBackupDriver {
  void performVersioning(DbClient dbClient, Configuration configuration, BackupDirectory backupDirectory);
}
