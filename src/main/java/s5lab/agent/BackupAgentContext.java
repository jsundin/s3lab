package s5lab.agent;

import s5lab.db.DbClient;
import s5lab.notification.NotificationService;

public class BackupAgentContext {
  public final DbClient dbClient;
  public final NotificationService notificationService;

  public BackupAgentContext(DbClient dbClient, NotificationService notificationService) {
    this.dbClient = dbClient;
    this.notificationService = notificationService;
  }
}
