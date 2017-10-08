package s5lab.notification;

public class Notification {
  private final Priority priority;
  private final String shortMessage;
  private final String longMessageSubject;
  private final String longMessageText;

  public Notification(Priority priority, String shortMessage, String longMessageSubject, String longMessageText) {
    this.priority = priority;
    this.shortMessage = shortMessage;
    this.longMessageSubject = longMessageSubject;
    this.longMessageText = longMessageText;
  }

  public Priority getPriority() {
    return priority;
  }

  public String getShortMessage() {
    return shortMessage;
  }

  public String getLongMessageSubject() {
    return longMessageSubject;
  }

  public String getLongMessageText() {
    return longMessageText;
  }

  public boolean hasShortMessage() {
    return shortMessage != null;
  }

  public boolean hasLongMessage() {
    return longMessageSubject != null && longMessageText != null;
  }

  @Override
  public String toString() {
    return "Notification{" +
            "priority=" + priority +
            ", shortMessage='" + shortMessage + '\'' +
            ", longMessageSubject='" + longMessageSubject + '\'' +
            ", longMessageText='" + longMessageText + '\'' +
            '}';
  }

  public enum Priority {
    NORMAL,
    HIGH
  }
}
