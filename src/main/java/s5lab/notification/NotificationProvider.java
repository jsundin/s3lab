package s5lab.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "provider")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GmailTlsNotificationProvider.class, name = "gmail"),
        @JsonSubTypes.Type(value = StdoutNotificationProvider.class, name = "stdout")
})
public interface NotificationProvider {
  void notify(Notification notification) throws NotificationException;
}
