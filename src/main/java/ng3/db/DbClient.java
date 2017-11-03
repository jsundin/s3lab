package ng3.db;

import ng3.BackupState;

public class DbClient {
  private final DbHandler dbHandler;

  DbClient(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public QueryBuilder buildQuery(String sql) {
    return new QueryBuilder(dbHandler.getConnection()).withStatement(sql);
  }

  public BackupState getBackupState() {
    BackupState state = buildQuery("select last_started from state")
            .executeQueryForObject(rs -> new BackupState(
                    rs.getTimestamp(1)
            ));
    if (state == null) {
      state = new BackupState(null);
    }
    return state;
  }

  public void saveBackupState(BackupState state) {
    int updates = buildQuery("update state set last_started=?")
            .withParam().timestampValue(1, state.getLastStarted())
            .executeUpdate();
    if (updates == 0) {
      buildQuery("insert into state (last_started) values (?)")
              .withParam().timestampValue(1, state.getLastStarted())
              .executeUpdate();
    }
  }
}
