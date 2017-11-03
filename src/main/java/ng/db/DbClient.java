package ng.db;

/**
 * @author johdin
 * @since 2017-11-03
 */
public class DbClient {
  private final DbHandler dbHandler;

  public DbClient(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public QueryBuilder buildQuery(String sql) {
    return new QueryBuilder(dbHandler.getConnection()).withStatement(sql);
  }
}
