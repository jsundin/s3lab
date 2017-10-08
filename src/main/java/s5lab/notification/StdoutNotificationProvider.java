package s5lab.notification;

public class StdoutNotificationProvider implements NotificationProvider {
  @Override
  public void notify(Notification notification) throws NotificationException {
    System.out.println("--- START NOTIFICATION ---");
    System.out.println(notification);
    System.out.println("---  END NOTIFICATION  ---");
  }
}
