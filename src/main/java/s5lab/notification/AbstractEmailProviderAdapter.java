package s5lab.notification;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;

abstract class AbstractEmailProviderAdapter {
  private  final String from;
  private final List<String> recipients;
  private final String subjectPrefix;

  public AbstractEmailProviderAdapter(String from, List<String> recipients, String subjectPrefix) {
    this.from = from;
    this.recipients = recipients;
    this.subjectPrefix = subjectPrefix;
  }

  protected MimeMessage createMessage(Session session, String subject, String bodyText) throws MessagingException {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(from));
    for (String recipient : recipients) {
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
    }
    message.setSubject(subjectPrefix == null ? "" : subjectPrefix + subject);
    message.setText(bodyText);
    return message;
  }
}
