package s5lab.agent;

import s5lab.db.DbClient;
import s5lab.notification.NotificationService;

import java.util.Queue;

public class BackupAgentContext {
  public final DbClient dbClient;
  public final NotificationService notificationService;
  public final Queue<FilescannerEvent> filescannerQueue;

  public BackupAgentContext(DbClient dbClient, NotificationService notificationService, Queue<FilescannerEvent> filescannerQueue) {
    this.dbClient = dbClient;
    this.notificationService = notificationService;
    this.filescannerQueue = filescannerQueue;
  }
}
