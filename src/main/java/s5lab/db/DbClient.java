package s5lab.db;

public class DbClient {
  private final DbHandler dbHandler;
  private final Repository repository = new Repository(this);

  DbClient(DbHandler dbHandler) {
    this.dbHandler = dbHandler;
  }

  public QueryBuilder buildQuery(String sql) {
    return new QueryBuilder(dbHandler.getConnection()).withStatement(sql);
  }

  public Repository getRepository() {
    return repository;
  }
}
