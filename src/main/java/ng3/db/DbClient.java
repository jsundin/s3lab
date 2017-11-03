package ng3.db;

import ng3.BackupPlan;

import java.util.UUID;

public class DbClient {
  private final DbHandler dbHandler;

  DbClient(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public QueryBuilder buildQuery(String sql) {
    return new QueryBuilder(dbHandler.getConnection()).withStatement(sql);
  }

  public BackupPlan getBackupPlan(UUID planId) {
    BackupPlan plan = buildQuery("select plan_id, last_started from plan where plan_id=?")
            .withParam().uuidValue(1, planId)
            .executeQueryForObject(rs -> {
              BackupPlan rplan = new BackupPlan(rs.getUuid(1));
              rplan.setLastStarted(rs.getTimestamp(2));
              return rplan;
            });
    return plan;
  }

  public void saveBackupPlan(BackupPlan plan) {
    buildQuery("update plan set last_started=? where plan_id=?")
            .withParam().timestampValue(1, plan.getLastStarted())
            .withParam().uuidValue(2, plan.getId())
            .executeUpdate();
  }
}
