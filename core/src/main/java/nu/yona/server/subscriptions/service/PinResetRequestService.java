package nu.yona.server.subscriptions.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.PinResetRequestConfirmationException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;

@Service
public class PinResetRequestService
{
	private static final Logger logger = LoggerFactory.getLogger(PinResetRequestService.class);

	@Autowired
	private UserService userService;

	@Autowired
	private YonaProperties yonaProperties;

	@Transactional
	public void requestPinReset(UUID userID)
	{
		User userEntity = userService.getUserByID(userID);
		logger.info("Received pin reset request for user with mobile number '{}' and ID '{}'", userEntity.getMobileNumber(),
				userID);
		ConfirmationCode confirmationCode = createConfirmationCode();
		setConfirmationCode(userEntity, confirmationCode);
		if (confirmationCode.getConfirmationCode() != null)
		{
			sendConfirmationCodeTextMessage(userEntity, confirmationCode);
		}
	}

	@Transactional
	public void verifyPinResetConfirmationCode(UUID userID, String userProvidedConfirmationCode)
	{
		User userEntity = userService.getUserByID(userID);
		logger.info("Received pin reset verification request for user with mobile number '{}' and ID '{}'",
				userEntity.getMobileNumber(), userID);
		ConfirmationCode confirmationCode = userEntity.getPinResetConfirmationCode();
		if ((confirmationCode == null) || isExpired(confirmationCode))
		{
			throw PinResetRequestConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber());
		}

		int remainingAttempts = yonaProperties.getSecurity().getConfirmationCodeMaxAttempts() - confirmationCode.getAttempts();
		if (remainingAttempts <= 0)
		{
			throw PinResetRequestConfirmationException.tooManyAttempts(userEntity.getMobileNumber());
		}

		if (!userProvidedConfirmationCode.equals(confirmationCode.getConfirmationCode()))
		{
			userService.registerFailedAttempt(userEntity, confirmationCode);
			throw PinResetRequestConfirmationException.confirmationCodeMismatch(userEntity.getMobileNumber(),
					userProvidedConfirmationCode, remainingAttempts - 1);
		}
	}

	@Transactional
	public void clearPinResetRequest(UUID userID)
	{
		User userEntity = userService.getUserByID(userID);
		logger.info("Received pin reset clearance request for user with mobile number '{}' and ID '{}'",
				userEntity.getMobileNumber(), userID);
		setConfirmationCode(userEntity, null);
	}

	public void sendConfirmationCodeTextMessage(User userEntity, ConfirmationCode confirmationCode)
	{
		userService.sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
				SmsService.TemplateName_AddUserNumberConfirmation);
	}

	private ConfirmationCode createConfirmationCode()
	{
		String confirmationCode = yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay().equals(Duration.ZERO)
				? userService.generateConfirmationCode() : null;
		return ConfirmationCode.createInstance(confirmationCode);
	}

	private void setConfirmationCode(User userEntity, ConfirmationCode confirmationCode)
	{
		userEntity.setPinResetConfirmationCode(confirmationCode);
		User.getRepository().save(userEntity);
	}

	public boolean isExpired(ConfirmationCode confirmationCode)
	{
		ZonedDateTime creationTime = confirmationCode.getCreationTime();
		return creationTime.plus(yonaProperties.getSecurity().getPinResetRequestExpirationTime()).isBefore(ZonedDateTime.now());
	}
}
