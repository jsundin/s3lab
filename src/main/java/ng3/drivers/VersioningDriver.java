package ng3.drivers;

import ng3.BackupDirectory;
import ng3.conf.Configuration;
import ng3.db.DbClient;

import java.util.List;

/**
 * @author johdin
 * @since 2017-11-17
 */
public interface VersioningDriver {
  void performVersioning(DbClient dbClient, Configuration configuration, List<BackupDirectory> backupDirectories);
}
