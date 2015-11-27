package nu.yona.server.email;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailSenderConfiguration
{
	@Value("${yona.email.smtp.protocol}")
	private String smtpProtocol;
	@Value("${yona.email.smtp.host}")
	private String smtpHost;
	@Value("${yona.email.smtp.port}")
	private int smtpPort;
	@Value("${yona.email.smtp.auth}")
	private boolean smtpUseAuth;
	@Value("${yona.email.smtp.starttls.enable}")
	private boolean smtpUseStarttls;
	@Value("${yona.email.smtp.username}")
	private String smtpUsername;
	@Value("${yona.email.smtp.password}")
	private String smtpPassword;

	@Bean
	public JavaMailSender javaMailSender()
	{
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		Properties mailProperties = new Properties();
		mailProperties.put("mail.smtp.auth", smtpUseAuth);
		mailProperties.put("mail.smtp.starttls.enable", smtpUseStarttls);
		mailSender.setJavaMailProperties(mailProperties);
		mailSender.setHost(smtpHost);
		mailSender.setPort(smtpPort);
		mailSender.setProtocol(smtpProtocol);
		mailSender.setUsername(smtpUsername);
		mailSender.setPassword(smtpPassword);
		return mailSender;
	}
}
