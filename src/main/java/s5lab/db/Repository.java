package s5lab.db;

import java.util.UUID;

public class Repository {
  private final DbClient dbClient;

  Repository(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  public boolean deleteJob(UUID id) {
    return dbClient.buildQuery("delete from job where id=?")
            .withParam().uuidValue(1, id)
            .executeUpdate() == 1;
  }
}
