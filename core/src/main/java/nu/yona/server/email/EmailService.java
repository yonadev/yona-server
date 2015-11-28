package nu.yona.server.email;

import java.util.Map;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;

import nu.yona.server.properties.YonaProperties;

@Service
public class EmailService
{
	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private JavaMailSender mailSender;
	@Autowired
	private VelocityEngine velocityEngine;

	public void sendEmail(String senderName, InternetAddress receiverAddress, String subjectTemplateName, String bodyTemplateName,
			Map<String, Object> templateParameters)
	{
		MimeMessagePreparator preparator = new MimeMessagePreparator() {
			public void prepare(MimeMessage mimeMessage) throws Exception
			{
				String subjectText = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine,
						"email/" + subjectTemplateName + ".vm", "UTF-8", templateParameters);
				String bodyText = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "email/" + bodyTemplateName + ".vm",
						"UTF-8", templateParameters);

				MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
				message.setFrom(new InternetAddress(yonaProperties.getEmail().getSenderAddress(), senderName));
				message.setTo(receiverAddress);
				message.setSubject(subjectText);
				message.setText(bodyText, true);
			}
		};
		mailSender.send(preparator);
	}
}
