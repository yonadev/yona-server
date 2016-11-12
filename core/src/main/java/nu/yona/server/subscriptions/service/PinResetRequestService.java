/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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

import nu.yona.server.exceptions.PinResetRequestConfirmationException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
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

	@Transactional
	public void requestPinReset(UUID userID)
	{
		User userEntity = userService.getUserEntityByID(userID);
		logger.info("User with mobile number '{}' and ID '{}' requested a pin reset confirmation code",
				userEntity.getMobileNumber(), userID);
		ConfirmationCode confirmationCode = createConfirmationCode(Moment.DELAYED);
		setConfirmationCode(userEntity, confirmationCode);
		if (confirmationCode.getConfirmationCode() != null)
		{
			sendConfirmationCodeTextMessage(userEntity, confirmationCode);
		}
	}

	@Transactional
	public void verifyPinResetConfirmationCode(UUID userID, String userProvidedConfirmationCode)
	{
		User userEntity = userService.getUserEntityByID(userID);
		logger.info("User with mobile number '{}' and ID '{}' requested to verity the pin reset confirmation code",
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
	public void resendPinResetConfirmationCode(UUID userID)
	{
		User userEntity = userService.getUserEntityByID(userID);
		logger.info("User with mobile number '{}' and ID '{}' requested to resend the pin reset confirmation code",
				userEntity.getMobileNumber(), userEntity.getID());
		ConfirmationCode confirmationCode = createConfirmationCode(Moment.IMMEDIATELY);
		setConfirmationCode(userEntity, confirmationCode);
		sendConfirmationCodeTextMessage(userEntity, confirmationCode);
	}

	@Transactional
	public void clearPinResetRequest(UUID userID)
	{
		User userEntity = userService.getUserEntityByID(userID);
		logger.info("User with mobile number '{}' and ID '{}' requested to clear the pin reset confirmation code",
				userEntity.getMobileNumber(), userEntity.getID());
		setConfirmationCode(userEntity, null);
	}

	public void sendConfirmationCodeTextMessage(User userEntity, ConfirmationCode confirmationCode)
	{
		userService.sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
				SmsService.TemplateName_PinResetRequestConfirmation);
	}

	private ConfirmationCode createConfirmationCode(Moment moment)
	{
		String confirmationCode = (moment == Moment.IMMEDIATELY)
				|| yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay().equals(Duration.ZERO)
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
		LocalDateTime creationTime = confirmationCode.getCreationTime();
		return creationTime.plus(yonaProperties.getSecurity().getPinResetRequestExpirationTime()).isBefore(TimeUtil.utcNow());
	}
}
