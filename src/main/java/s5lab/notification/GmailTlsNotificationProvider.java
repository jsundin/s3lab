package s5lab.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

public class GmailTlsNotificationProvider extends AbstractEmailProviderAdapter implements NotificationProvider {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final PasswordAuthentication passwordAuthentication;

  @JsonCreator
  public GmailTlsNotificationProvider(
          @JsonProperty("username") String username,
          @JsonProperty("password") String password,
          @JsonProperty("from") String from,
          @JsonProperty("to") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> to,
          @JsonProperty("subject-prefix") String subjectPrefix) {
    super(from, to, subjectPrefix);
    passwordAuthentication = new PasswordAuthentication(username, password);
  }

  @Override
  public void notify(Notification notification) throws NotificationException {
    if (notification.hasLongMessage()) {
      Properties props = new Properties();
      props.put("mail.smtp.auth", "true");
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.host", "smtp.gmail.com");
      props.put("mail.smtp.port", "587");

      Session session = Session.getDefaultInstance(props, new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return passwordAuthentication;
        }
      });

      MimeMessage message = null;
      try {
        message = createMessage(session, notification.getLongMessageSubject(), notification.getLongMessageText());
        Transport.send(message);
      } catch (MessagingException e) {
        logger.error("Could not send email", e);
        throw new NotificationException(e);
      }
    }
  }
}
