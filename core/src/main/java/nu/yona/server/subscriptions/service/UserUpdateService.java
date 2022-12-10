/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.exceptions.YonaException;
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
import nu.yona.server.subscriptions.entities.UserPhoto;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.util.Require;
import nu.yona.server.util.TimeUtil;

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
	@Lazy
	private BuddyService buddyService;

	@Autowired(required = false)
	private SmsService smsService;

	@Autowired(required = false)
	@Lazy
	private DeviceService deviceService;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	@Lazy
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
		return (yonaProperties.getSms().isEnabled()) ?
				CryptoUtil.getRandomDigits(yonaProperties.getSecurity().getConfirmationCodeDigits()) :
				"1234";
	}

	ConfirmationCode createConfirmationCode()
	{
		return ConfirmationCode.createInstance(generateConfirmationCode());
	}

	@Transactional
	public void requestOverwriteUserConfirmationCode(String mobileNumber)
	{
		updateUser(UserLookupService.findUserByMobileNumber(mobileNumber).getId(),
				user -> setOverwriteUserConfirmationCodeIfNotDoneRecently(mobileNumber, user));
	}

	private void setOverwriteUserConfirmationCodeIfNotDoneRecently(String mobileNumber, User user)
	{
		try
		{
			logger.info("DEBUG: begin UserUpdateService.setOverwriteUserConfirmationCodeIfNotDoneRecently for {}", mobileNumber);
			if (isOverwriteUserConfirmationCodeStillValid(user.getOverwriteUserConfirmationCode()))
			{
				logger.info(
						"User with mobile number '{}' and ID '{}' requested an account overwrite confirmation code, but the current one 'DEBUG {}' is still valid, so no new code is sent",
						user.getMobileNumber(), user.getId(), user.getOverwriteUserConfirmationCode().get().getId());
				return;
			}
			ConfirmationCode confirmationCode = createConfirmationCode();
			user.setOverwriteUserConfirmationCode(confirmationCode);
			sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, SmsTemplate.OVERWRITE_USER_CONFIRMATION);
			logger.info(
					"User with mobile number '{}' and ID '{}' requested an account overwrite confirmation code 'DEBUG created {}'",
					user.getMobileNumber(), user.getId(), confirmationCode.getId());
		}
		finally
		{
			logger.info("DEBUG: end UserUpdateService.setOverwriteUserConfirmationCodeIfNotDoneRecently for {}", mobileNumber);
		}
	}

	private boolean isOverwriteUserConfirmationCodeStillValid(Optional<ConfirmationCode> overwriteUserConfirmationCode)
	{
		return overwriteUserConfirmationCode.map(this::isOverwriteUserConfirmationCodeStillValid).orElse(false);
	}

	private boolean isOverwriteUserConfirmationCodeStillValid(ConfirmationCode overwriteUserConfirmationCode)
	{
		return overwriteUserConfirmationCode.getCreationTime()
				.isAfter(TimeUtil.utcNow().minus(yonaProperties.getOverwriteUserConfirmationCodeNonResendInterval()));
	}

	@Transactional
	public UserDto updateUser(UUID id, UserDto user)
	{
		User updatedUserEntity = updateUser(id, userEntity -> {
			assertValidUpdateRequestForUserCreatedNormally(user, userEntity);
			UserDto originalUser = userLookupService.createUserDto(userEntity);

			updateUser(user, userEntity, originalUser);
			sendConfirmationCodeIfNumberUpdated(user, userEntity, originalUser);

			UserDto userDto = userLookupService.createUserDto(userEntity);
			logger.info("Updated user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
			buddyService.broadcastUserInfoChangeToBuddies(userEntity, originalUser);
		});
		return userLookupService.createUserDto(updatedUserEntity);
	}

	private void sendConfirmationCodeIfNumberUpdated(UserDto user, User userEntity, UserDto originalUser)
	{
		Optional<ConfirmationCode> confirmationCode = createConfirmationCodeIfNumberUpdated(user.getMobileNumber(),
				originalUser.getMobileNumber(), userEntity);
		if (confirmationCode.isPresent())
		{
			sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
					SmsTemplate.CHANGED_USER_NUMBER_CONFIRMATION);
		}
	}

	private void updateUser(UserDto user, User userEntity, UserDto originalUser)
	{
		if (user.getCreationTime().isPresent() && !containsPropertyUpdate(originalUser, user))
		{
			Require.that(yonaProperties.isTestServer(),
					() -> InvalidDataException.onlyAllowedOnTestServers("Cannot set user creation time"));
			// Tests update the creation time. Handle that as a special case.
			LocalDateTime creationTime = user.getCreationTime().get();
			userEntity.setCreationTime(creationTime);
			userEntity.getBuddies().forEach(b -> b.setLastStatusChangeTime(creationTime));
		}
		else
		{
			Require.isNotPresent(user.getCreationTime(),
					() -> InvalidDataException.extraProperty("creationTime", "Creation time cannot be changed"));
			user.updateUser(userEntity);
		}
	}

	private boolean containsPropertyUpdate(UserDto existingUser, UserDto newUser)
	{
		OwnUserPrivateDataDto existingPrivateData = existingUser.getOwnPrivateData();
		OwnUserPrivateDataDto newPrivateData = newUser.getOwnPrivateData();
		return !newUser.getMobileNumber().equals(existingUser.getMobileNumber()) || !existingPrivateData.getFirstName()
				.equals(newPrivateData.getFirstName()) || !existingPrivateData.getLastName().equals(newPrivateData.getLastName())
				|| !existingPrivateData.getNickname().equals(newPrivateData.getNickname())
				|| newPrivateData.getYonaPassword() != null && !existingPrivateData.getYonaPassword()
				.equals(newPrivateData.getYonaPassword());

	}

	/**
	 * Performs the given update action on the user with the specified ID, while holding a write-lock on the user. After the
	 * update, the entity is saved to the repository.<br/>
	 * We are using pessimistic locking because we generally do not have update concurrency, except when performing the user
	 * preparation (migration steps, processing messages, handling buddies deleted while offline, etc.). This preparation is
	 * executed during GET-requests and GET-requests can come concurrently. Optimistic locking wouldn't be an option here as that
	 * would cause the GETs to fail rather than to wait for the other one to complete.
	 *
	 * @param id           The ID of the user to update
	 * @param updateAction The update action to perform
	 * @return The updated and saved user
	 */
	@Transactional(dontRollbackOn = { MobileNumberConfirmationException.class, UserOverwriteConfirmationException.class })
	public User updateUser(UUID id, Consumer<User> updateAction)
	{
		// We add a lock here to prevent concurrent user updates.
		// The lock is per user, so concurrency is not an issue.
		return userLookupService.withLockOnUser(id, user -> updateUser(user, updateAction));
	}

	/**
	 * Performs the given update action on the user with the specified ID (if the user exists), while holding a write-lock on the
	 * user. After the update, the entity is saved to the repository.<br/>
	 * We are using pessimistic locking because we generally do not have update concurrency, except when performing the user
	 * preparation (migration steps, processing messages, handling buddies deleted while offline, etc.). This preparation is
	 * executed during GET-requests and GET-requests can come concurrently. Optimistic locking wouldn't be an option here as that
	 * would cause the GETs to fail rather than to wait for the other one to complete.
	 *
	 * @param id           The ID of the user to update
	 * @param updateAction The update action to perform
	 * @return an {@code Optional} describing the updated and saved user, or an empty {@code Optional} if a user with this ID
	 * cannot be found
	 */
	@Transactional(dontRollbackOn = { MobileNumberConfirmationException.class, UserOverwriteConfirmationException.class })
	public Optional<User> updateUserIfExisting(UUID id, Consumer<User> updateAction)
	{
		// We add a lock here to prevent concurrent user updates.
		// The lock is per user, so concurrency is not an issue.
		return userLookupService.withLockOnUserIfExisting(id, user -> updateUser(user, updateAction));
	}

	/**
	 * Applies the given udpate action on the user and saves the user to the repository.<br/>
	 * NOTE: This method is intended to be called while holding a pessimistic lock on the user entity.
	 *
	 * @param user         The user to update
	 * @param updateAction The update action to perform
	 * @return the updated and saved user
	 */
	private User updateUser(User user, Consumer<User> updateAction)
	{
		updateAction.accept(user);
		if (user.canAccessPrivateData())
		{
			// The private data is accessible and might be updated, including the UserAnonymized
			// Let the UserAnonymizedService save that to the repository and cache it
			userAnonymizedService.updateUserAnonymized(user.getAnonymized());
		}
		return userRepository.save(user);
	}

	@Transactional
	public UserDto updateUserPhoto(User userEntity, Optional<UserPhoto> userPhoto)
	{
		userAssertionService.assertUserEntityLockedForUpdate(userEntity);
		UserDto originalUser = userLookupService.createUserDto(userEntity);
		userEntity.setUserPhotoId(userPhoto.map(UserPhoto::getId));
		UserDto userDto = userLookupService.createUserDto(userEntity);
		if (userPhoto.isPresent())
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
		return userLookupService.createUserDto(userRepository.save(userEntity));
	}

	private void assertValidUpdateRequestForUserCreatedNormally(UserDto user, User originalUserEntity)
	{
		// security check: should not be able to update a user created on buddy request with its temp password
		Require.that(!originalUserEntity.isCreatedOnBuddyRequest(),
				() -> UserServiceException.cannotUpdateBecauseCreatedOnBuddyRequest(user.getId()));

		assertValidUpdateRequest(user, originalUserEntity);
	}

	private void assertValidUpdateRequestForUserCreatedOnBuddyRequest(UserDto user, User originalUserEntity)
	{
		// security check: should not be able to replace the password on an existing user
		Require.that(originalUserEntity.isCreatedOnBuddyRequest(),
				() -> UserServiceException.userNotCreatedOnBuddyRequest(originalUserEntity.getId()));

		assertValidUpdateRequest(user, originalUserEntity);
	}

	private void assertValidUpdateRequest(UserDto user, User originalUserEntity)
	{
		if (isMobileNumberDifferent(user.getMobileNumber(), originalUserEntity.getMobileNumber()))
		{
			userAssertionService.assertUserDoesNotExist(user.getMobileNumber());
		}
	}

	private Optional<ConfirmationCode> createConfirmationCodeIfNumberUpdated(String currentNumber, String originalNumber,
			User updatedUserEntity)
	{
		if (isMobileNumberDifferent(currentNumber, originalNumber))
		{
			ConfirmationCode confirmationCode = createConfirmationCode();
			updatedUserEntity.setMobileNumberConfirmationCode(confirmationCode);
			return Optional.of(confirmationCode);
		}
		return Optional.empty();
	}

	private boolean isMobileNumberDifferent(String currentNumber, String originalNumber)
	{
		return !currentNumber.equals(originalNumber);
	}

	@Transactional
	public UserDto updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDto user)
	{
		User originalUserEntity = userLookupService.lockUserForUpdate(id);

		assertValidUpdateRequestForUserCreatedOnBuddyRequest(user, originalUserEntity);

		EncryptedUserData retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		User savedUserEntity = saveUserEncryptedDataWithNewPassword(retrievedEntitySet, user);
		ConfirmationCode mobileNumberConfirmationCode = savedUserEntity.getMobileNumberConfirmationCode()
				.orElseThrow(() -> YonaException.illegalState("Mobile number confirmation code must exist in this state"));
		sendConfirmationCodeTextMessage(savedUserEntity.getMobileNumber(), mobileNumberConfirmationCode,
				SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
		UserDto userDto = userLookupService.createUserDto(savedUserEntity);
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
	public void addBuddy(User userEntity, BuddyDto buddy)
	{
		Require.that(userEntity != null && userEntity.getId() != null, InvalidDataException::emptyUserId);
		Require.that(buddy != null && buddy.getId() != null, InvalidDataException::emptyBuddyId);
		userAssertionService.assertUserEntityLockedForUpdate(userEntity);

		userAssertionService.assertValidatedUser(userEntity);

		Buddy buddyEntity = buddyRepository.findById(buddy.getId())
				.orElseThrow(() -> InvalidDataException.missingEntity(Buddy.class, buddy.getId()));
		userEntity.addBuddy(buddyEntity);

		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		userAnonymizedEntity.addBuddyAnonymized(buddyEntity.getBuddyAnonymized());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
	}

	private User saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDto userDtoWithNewData)
	{
		assert userDtoWithNewData.getPrivateData().getDevices().orElse(Collections.emptySet()).size() == 1;
		// touch and save all user related data containing encryption
		// see architecture overview for which classes contain encrypted data
		// (this could also be achieved with very complex reflection)
		User userEntity = retrievedEntitySet.userEntity;
		userEntity.getBuddies().forEach(buddy -> buddyRepository.save(buddy.touch()));
		messageSourceRepository.save(retrievedEntitySet.namedMessageSource.touch());
		messageSourceRepository.save(retrievedEntitySet.anonymousMessageSource.touch());
		userDtoWithNewData.updateUser(userEntity);
		userEntity.unsetIsCreatedOnBuddyRequest();
		addDevicesToEntity(userDtoWithNewData, userEntity);
		userEntity.touch();

		return userRepository.save(userEntity);
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
