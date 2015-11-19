package nu.yona.server.subscriptions.service;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.springframework.stereotype.Service;

@Service
public class EmailService
{
	public void sendEmail(String to, String subject, String text)
	{
		Properties props = new Properties();
		props.put("mail.smtp.host", "smtp.gmail.com"); // TODO: replace by local SMTP server
		props.put("mail.smtp.socketFactory.port", "465");
		props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.port", "465");

		Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication()
			{
				return new PasswordAuthentication("bobdunn325@gmail.com", "bobbydunn");
			}
		});

		try
		{

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress("noreply@yona.nu"));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
			message.setSubject(subject);
			message.setText(text);

			Transport.send(message);

		}
		catch (MessagingException e)
		{
			throw new RuntimeException(e);
		}
	}
}
