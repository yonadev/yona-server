/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.batch.client.BatchProxyService;
import nu.yona.server.exceptions.PinResetRequestConfirmationException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.util.Require;
import nu.yona.server.util.TimeUtil;

@Service
public class PinResetRequestService
{
	private static final Logger logger = LoggerFactory.getLogger(PinResetRequestService.class);

	private enum Moment
	{
		IMMEDIATELY, DELAYED
	}

	@Autowired
	private UserService userService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private BatchProxyService batchProxyService;

	@Transactional
	public void requestPinReset(UUID userId)
	{
		User userEntity = userService.getUserEntityById(userId);
		logger.info("User with mobile number '{}' and ID '{}' requested a pin reset confirmation code",
				userEntity.getMobileNumber(), userId);
		ConfirmationCode confirmationCode = createConfirmationCode(Moment.DELAYED);
		setConfirmationCode(userId, confirmationCode);
		if (confirmationCode.getCode() == null)
		{
			LocalDateTime executionTime = TimeUtil.utcNow()
					.plus(yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay());
			batchProxyService.requestPinResetConfirmationCode(userId, executionTime);
		}
		else
		{
			sendConfirmationCodeTextMessage(userEntity, confirmationCode);
		}
	}

	@Transactional
	public void sendPinResetConfirmationCode(UUID userId)
	{
		User user = userService.getUserEntityById(userId);
		logger.info("Generating pin reset confirmation code for user with mobile number '{}' and ID '{}'", user.getMobileNumber(),
				user.getId());
		ConfirmationCode pinResetConfirmationCode = user.getPinResetConfirmationCode();
		pinResetConfirmationCode.setCode(userService.generateConfirmationCode());
		sendConfirmationCodeTextMessage(user, pinResetConfirmationCode);
	}

	@Transactional(dontRollbackOn = PinResetRequestConfirmationException.class)
	public void verifyPinResetConfirmationCode(UUID userId, String userProvidedConfirmationCode)
	{
		User userEntity = userService.getUserEntityById(userId);
		logger.info("User with mobile number '{}' and ID '{}' requested to verify the pin reset confirmation code",
				userEntity.getMobileNumber(), userId);
		ConfirmationCode confirmationCode = userEntity.getPinResetConfirmationCode();
		Require.that((confirmationCode != null) && !isExpired(confirmationCode),
				() -> PinResetRequestConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber()));

		int remainingAttempts = yonaProperties.getSecurity().getConfirmationCodeMaxAttempts() - confirmationCode.getAttempts();
		Require.that(remainingAttempts > 0,
				() -> PinResetRequestConfirmationException.tooManyAttempts(userEntity.getMobileNumber()));

		if (!userProvidedConfirmationCode.equals(confirmationCode.getCode()))
		{
			userService.registerFailedAttempt(userEntity, confirmationCode);
			throw PinResetRequestConfirmationException.confirmationCodeMismatch(userEntity.getMobileNumber(),
					userProvidedConfirmationCode, remainingAttempts - 1);
		}
	}

	@Transactional
	public void resendPinResetConfirmationCode(UUID userId)
	{
		User userEntity = userService.getUserEntityById(userId);
		logger.info("User with mobile number '{}' and ID '{}' requested to resend the pin reset confirmation code",
				userEntity.getMobileNumber(), userEntity.getId());
		ConfirmationCode confirmationCode = createConfirmationCode(Moment.IMMEDIATELY);
		setConfirmationCode(userId, confirmationCode);
		sendConfirmationCodeTextMessage(userEntity, confirmationCode);
	}

	@Transactional
	public void clearPinResetRequest(UUID userId)
	{
		User userEntity = userService.getUserEntityById(userId);
		logger.info("User with mobile number '{}' and ID '{}' requested to clear the pin reset confirmation code",
				userEntity.getMobileNumber(), userEntity.getId());
		setConfirmationCode(userId, null);
	}

	private void sendConfirmationCodeTextMessage(User userEntity, ConfirmationCode confirmationCode)
	{
		userService.sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
				SmsTemplate.PIN_RESET_REQUEST_CONFIRMATION);
	}

	private ConfirmationCode createConfirmationCode(Moment moment)
	{
		String confirmationCode = (moment == Moment.IMMEDIATELY)
				|| yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay().equals(Duration.ZERO)
						? userService.generateConfirmationCode()
						: null;
		return ConfirmationCode.createInstance(confirmationCode);
	}

	private void setConfirmationCode(UUID userId, ConfirmationCode confirmationCode)
	{
		userService.updateUser(userId, user -> user.setPinResetConfirmationCode(confirmationCode));
	}

	public boolean isExpired(ConfirmationCode confirmationCode)
	{
		LocalDateTime creationTime = confirmationCode.getCreationTime();
		return creationTime.plus(yonaProperties.getSecurity().getPinResetRequestExpirationTime()).isBefore(TimeUtil.utcNow());
	}
}
