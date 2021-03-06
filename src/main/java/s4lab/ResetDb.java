package s4lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.conf.Settings;
import s4lab.db.DbHandler;

import java.sql.SQLException;

public class ResetDb {
  private static final Logger logger = LoggerFactory.getLogger(ResetDb.class);

  public static void main(String[] args) throws Exception {
    DbHandler dbh = new DbHandler(Settings.JDBC_URL, Settings.JDBC_USERNAME, Settings.JDBC_PASSWORD, Settings.JDBC_POOL_SIZE);
    dbh.start();
    try {
      logger.info("Dropping old tables");
      dbh.dropDatabase();
    } catch (SQLException e) {
      logger.warn("Could not drop old tables -- proceeding anyway", e);
    }

    logger.info("Creating database");
    dbh.createDatabase();

    dbh.finish();
  }
}
