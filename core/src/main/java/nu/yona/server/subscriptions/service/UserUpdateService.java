/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyRepository;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.util.Require;

@Service
public class UserUpdateService
{
	private static final Logger logger = LoggerFactory.getLogger(UserUpdateService.class);

	@Autowired(required = false)
	private UserRepository userRepository;

	@Autowired(required = false)
	private MessageSourceRepository messageSourceRepository;

	@Autowired(required = false)
	private BuddyRepository buddyRepository;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired(required = false)
	private BuddyService buddyService;

	@Autowired(required = false)
	private SmsService smsService;

	@Autowired(required = false)
	private DeviceService deviceService;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private MessageService messageService;

	@Autowired(required = false)
	private UserAssertionService userAssertionService;

	@Autowired(required = false)
	private UserLookupService userLookupService;

	public void sendConfirmationCodeTextMessage(String mobileNumber, ConfirmationCode confirmationCode, SmsTemplate template)
	{
		Map<String, Object> templateParams = new HashMap<>();
		templateParams.put("confirmationCode", confirmationCode.getCode());
		smsService.send(mobileNumber, template, templateParams);
	}

	public String generateConfirmationCode()
	{
		return (yonaProperties.getSms().isEnabled())
				? CryptoUtil.getRandomDigits(yonaProperties.getSecurity().getConfirmationCodeDigits())
				: "1234";
	}

	ConfirmationCode createConfirmationCode()
	{
		return ConfirmationCode.createInstance(generateConfirmationCode());
	}

	@Transactional
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		updateUser(UserLookupService.findUserByMobileNumber(mobileNumber).getId(), existingUserEntity -> {
			ConfirmationCode confirmationCode = createConfirmationCode();
			existingUserEntity.setOverwriteUserConfirmationCode(confirmationCode);
			sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, SmsTemplate.OVERWRITE_USER_CONFIRMATION);
			logger.info("User with mobile number '{}' and ID '{}' requested an account overwrite confirmation code",
					existingUserEntity.getMobileNumber(), existingUserEntity.getId());
		});
	}

	@Transactional
	public UserDto updateUser(UUID id, UserDto user)
	{
		User updatedUserEntity = updateUser(id, userEntity -> {
			UserDto originalUser = userLookupService.createUserDtoWithPrivateData(userEntity);
			assertValidUpdateRequest(user, originalUser, userEntity);

			user.updateUser(userEntity);
			Optional<ConfirmationCode> confirmationCode = createConfirmationCodeIfNumberUpdated(user, originalUser, userEntity);
			if (confirmationCode.isPresent())
			{
				sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
						SmsTemplate.CHANGED_USER_NUMBER_CONFIRMATION);
			}
			UserDto userDto = userLookupService.createUserDtoWithPrivateData(userEntity);
			logger.info("Updated user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
			buddyService.broadcastUserInfoChangeToBuddies(userEntity, originalUser);
		});
		return userLookupService.createUserDtoWithPrivateData(updatedUserEntity);
	}

	/**
	 * Performs the given update action on the user with the specified ID, while holding a write-lock on the user. After the
	 * update, the entity is saved to the repository.<br/>
	 * We are using pessimistic locking because we generally do not have update concurrency, except when performing the user
	 * preparation (migration steps, processing messages, handling buddies deleted while offline, etc.). This preparation is
	 * executed during GET-requests and GET-requests can come concurrently. Optimistic locking wouldn't be an option here as that
	 * would cause the GETs to fail rather than to wait for the other one to complete.
	 * 
	 * @param id The ID of the user to update
	 * @param updateAction The update action to perform
	 * @return The updated and saved user
	 */
	@Transactional(dontRollbackOn = { MobileNumberConfirmationException.class, UserOverwriteConfirmationException.class })
	public User updateUser(UUID id, Consumer<User> updateAction)
	{
		// We add a lock here to prevent concurrent user updates.
		// The lock is per user, so concurrency is not an issue.
		return userLookupService.withLockOnUser(id, user -> {
			updateAction.accept(user);
			if (user.canAccessPrivateData())
			{
				// The private data is accessible and might be updated, including the UserAnonymized
				// Let the UserAnonymizedService save that to the repository and cache it
				userAnonymizedService.updateUserAnonymized(user.getAnonymized());
			}
			return userRepository.save(user);
		});
	}

	@Transactional
	public UserDto updateUserPhoto(UUID id, Optional<UUID> userPhotoId)
	{
		User updatedUserEntity = updateUser(id, userEntity -> {
			UserDto originalUser = userLookupService.createUserDtoWithPrivateData(userEntity);
			userEntity.setUserPhotoId(userPhotoId);
			UserDto userDto = userLookupService.createUserDtoWithPrivateData(userEntity);
			if (userPhotoId.isPresent())
			{
				logger.info("Updated user photo for user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(),
						userDto.getId());
			}
			else
			{
				logger.info("Deleted user photo for user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(),
						userDto.getId());
			}
			buddyService.broadcastUserInfoChangeToBuddies(userEntity, originalUser);
		});
		return userLookupService.createUserDtoWithPrivateData(updatedUserEntity);
	}

	private void assertValidUpdateRequest(UserDto user, UserDto originalUser, User originalUserEntity)
	{
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to update a user created on buddy request with its temp password
			throw UserServiceException.cannotUpdateBecauseCreatedOnBuddyRequest(user.getId());
		}
		if (isMobileNumberDifferent(user, originalUser))
		{
			userAssertionService.assertUserDoesNotExist(user.getMobileNumber());
		}
	}

	private Optional<ConfirmationCode> createConfirmationCodeIfNumberUpdated(UserDto user, UserDto originalUser,
			User updatedUserEntity)
	{
		if (isMobileNumberDifferent(user, originalUser))
		{
			ConfirmationCode confirmationCode = createConfirmationCode();
			updatedUserEntity.setMobileNumberConfirmationCode(confirmationCode);
			return Optional.of(confirmationCode);
		}
		return Optional.empty();
	}

	private boolean isMobileNumberDifferent(UserDto user, UserDto originalUser)
	{
		return !user.getMobileNumber().equals(originalUser.getMobileNumber());
	}

	@Transactional
	public UserDto updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDto user)
	{
		User originalUserEntity = userLookupService.getUserEntityById(id);
		// security check: should not be able to replace the password on an existing user
		Require.that(originalUserEntity.isCreatedOnBuddyRequest(), () -> UserServiceException.userNotCreatedOnBuddyRequest(id));

		EncryptedUserData retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		User savedUserEntity = saveUserEncryptedDataWithNewPassword(retrievedEntitySet, user);
		sendConfirmationCodeTextMessage(savedUserEntity.getMobileNumber(), savedUserEntity.getMobileNumberConfirmationCode(),
				SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
		UserDto userDto = userLookupService.createUserDtoWithPrivateData(savedUserEntity);
		logger.info("Updated user (created on buddy request) with mobile number '{}' and ID '{}'", userDto.getMobileNumber(),
				userDto.getId());
		return userDto;
	}

	private EncryptedUserData retrieveUserEncryptedData(User originalUserEntity, String password)
	{
		// use a separate crypto session to read the data based on the temporary password
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(password),
				() -> userLookupService.doPreparationsAndCheckCanAccessPrivateData(originalUserEntity.getId())))
		{
			return retrieveUserEncryptedDataInNewCryptoSession(originalUserEntity);
		}
	}

	private EncryptedUserData retrieveUserEncryptedDataInNewCryptoSession(User originalUserEntity)
	{
		MessageSource namedMessageSource = messageService.getMessageSource(originalUserEntity.getNamedMessageSourceId());
		MessageSource anonymousMessageSource = messageService.getMessageSource(originalUserEntity.getAnonymousMessageSourceId());
		EncryptedUserData userEncryptedData = new EncryptedUserData(originalUserEntity, namedMessageSource,
				anonymousMessageSource);
		userEncryptedData.loadLazyEncryptedData();
		return userEncryptedData;
	}

	@Transactional
	public void addBuddy(UserDto user, BuddyDto buddy)
	{
		Require.that(user != null && user.getId() != null, InvalidDataException::emptyUserId);
		Require.that(buddy != null && buddy.getId() != null, InvalidDataException::emptyBuddyId);

		updateUser(user.getId(), userEntity -> {
			userAssertionService.assertValidatedUser(userEntity);

			Buddy buddyEntity = buddyRepository.findById(buddy.getId())
					.orElseThrow(() -> InvalidDataException.missingEntity(Buddy.class, buddy.getId()));
			userEntity.addBuddy(buddyEntity);

			UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
			userAnonymizedEntity.addBuddyAnonymized(buddyEntity.getBuddyAnonymized());
			userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		});
	}

	private User saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDto user)
	{
		return updateUser(retrievedEntitySet.userEntity.getId(), userEntity -> {
			assert user.getPrivateData().getDevices().orElse(Collections.emptySet()).size() == 1;
			// touch and save all user related data containing encryption
			// see architecture overview for which classes contain encrypted data
			// (this could also be achieved with very complex reflection)
			userEntity.getBuddies().forEach(buddy -> buddyRepository.save(buddy.touch()));
			messageSourceRepository.save(retrievedEntitySet.namedMessageSource.touch());
			messageSourceRepository.save(retrievedEntitySet.anonymousMessageSource.touch());
			user.updateUser(userEntity);
			userEntity.unsetIsCreatedOnBuddyRequest();
			addDevicesToEntity(user, userEntity);
			userEntity.touch();
		});
	}

	private void addDevicesToEntity(UserDto userDto, User userEntity)
	{
		userRepository.saveAndFlush(userEntity); // To prevent sequence issues when saving the device
		userDto.getOwnPrivateData().getDevices().orElse(Collections.emptySet()).stream().map(d -> (UserDeviceDto) d)
				.forEach(d -> deviceService.addDeviceToUser(userEntity, d));
	}

	/**
	 * Gathers all entities that contain encrypted data from the database.
	 */
	static class EncryptedUserData
	{
		final User userEntity;
		final MessageSource namedMessageSource;
		final MessageSource anonymousMessageSource;

		EncryptedUserData(User userEntity, MessageSource namedMessageSource, MessageSource anonymousMessageSource)
		{
			this.userEntity = userEntity;
			this.namedMessageSource = namedMessageSource;
			this.anonymousMessageSource = anonymousMessageSource;
		}

		public void loadLazyEncryptedData()
		{
			// load encrypted data fully, also from lazy relations
			// see architecture overview for which classes contain encrypted data
			// the relation to user private is currently the only lazy relation
			// (this could also be achieved with very complex reflection)
			this.userEntity.loadFully();
		}
	}
}
