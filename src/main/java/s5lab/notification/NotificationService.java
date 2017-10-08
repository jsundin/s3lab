package s5lab.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class NotificationService {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final List<NotificationProvider> notificationProviders;

  public NotificationService(List<NotificationProvider> notificationProviders) {
    this.notificationProviders = notificationProviders;
  }

  public void notify(Notification notification) {
    notify(notification.getPriority(), notification.getShortMessage(), notification.getLongMessageSubject(), notification.getLongMessageText());
  }

  public void notify(Notification.Priority priority, String shortMessage, String longMessageSubject, String longMessageText) {
    final Notification notification = new Notification(priority, shortMessage, longMessageSubject, longMessageText);
    notificationProviders.forEach(np -> {
      try {
        np.notify(notification);
      } catch (NotificationException e) {
        logger.error("Notification failed!", e);
      }
    });
  }

  public NotificationBuilder newNotification() {
    return new NotificationBuilder();
  }

  public class NotificationBuilder {
    private Notification.Priority priority;
    private String shortMessage;
    private String longMessageSubject;
    private String longMessageText;
    private Throwable exception;

    public NotificationBuilder withPriority(Notification.Priority priority) {
      this.priority = priority;
      return this;
    }

    public NotificationBuilder withShortMessage(String message) {
      this.shortMessage = message;
      return this;
    }

    public NotificationBuilder withSubject(String subject) {
      this.longMessageSubject = subject;
      return this;
    }

    public NotificationBuilder withText(String text) {
      this.longMessageText = text;
      return this;
    }

    public NotificationBuilder withException(Throwable exception) {
      this.exception = exception;
      return this;
    }

    public Notification build() {
      if (exception != null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exception.printStackTrace(new PrintStream(baos));
        String exceptionTrace = baos.toString();
        longMessageText = (longMessageText == null ? "" : longMessageText + "\n--\n") + "Exception trace:\n" + exceptionTrace;
      }

      if (shortMessage != null && longMessageText != null && longMessageSubject == null) {
        return new Notification(priority, shortMessage, shortMessage, longMessageText);
      }

      return new Notification(priority, shortMessage, longMessageSubject, longMessageText);
    }
  }
}
