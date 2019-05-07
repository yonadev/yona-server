/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;

@Service
public class UserService
{
	enum UserPurpose
	{
		USER, BUDDY
	}

	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private UserRetrievalService userRetrievalService;

	@Autowired
	private UserUpdateService userUpdateService;

	@Autowired
	private UserAdditionService userAdditionService;

	@Autowired
	private UserDeletionService userDeletionService;

	@Transactional
	public boolean doPreparationsAndCheckCanAccessPrivateData(UUID id)
	{
		return userRetrievalService.doPreparationsAndCheckCanAccessPrivateData(id);
	}

	@Transactional
	public UserDto getPublicUser(UUID id)
	{
		return userRetrievalService.getPublicUser(id);
	}

	@Transactional
	public UserDto getPrivateUser(UUID id)
	{
		return userRetrievalService.getPrivateUser(id);
	}

	@Transactional
	public UserDto getPrivateUser(UUID id, boolean isCreatedOnBuddyRequest)
	{
		return userRetrievalService.getPrivateUser(id, isCreatedOnBuddyRequest);
	}

	@Transactional
	public UserDto getPrivateValidatedUser(UUID id)
	{
		return userRetrievalService.getPrivateValidatedUser(id);
	}

	@Transactional
	public User getPrivateValidatedUserEntity(UUID id)
	{
		return userRetrievalService.getPrivateValidatedUserEntity(id);
	}

	@Transactional
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		userUpdateService.setOverwriteUserConfirmationCode(mobileNumber);
	}

	@Transactional(dontRollbackOn = UserOverwriteConfirmationException.class)
	public UserDto addUser(UserDto user, Optional<String> overwriteUserConfirmationCode)
	{
		return userAdditionService.addUser(user, overwriteUserConfirmationCode);
	}

	public UserDto createUserDtoWithPrivateData(User user)
	{
		return userRetrievalService.createUserDtoWithPrivateData(user);
	}

	@Transactional(dontRollbackOn = MobileNumberConfirmationException.class)
	public UserDto confirmMobileNumber(UUID userId, String userProvidedConfirmationCode)
	{
		User updatedUserEntity = updateUser(userId, userEntity -> {
			ConfirmationCode confirmationCode = userEntity.getMobileNumberConfirmationCode();

			userAdditionService.assertValidConfirmationCode(userEntity, confirmationCode, userProvidedConfirmationCode,
					() -> MobileNumberConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber()),
					r -> MobileNumberConfirmationException.confirmationCodeMismatch(userEntity.getMobileNumber(),
							userProvidedConfirmationCode, r),
					() -> MobileNumberConfirmationException.tooManyAttempts(userEntity.getMobileNumber()));

			if (userEntity.isMobileNumberConfirmed())
			{
				throw MobileNumberConfirmationException.mobileNumberAlreadyConfirmed(userEntity.getMobileNumber());
			}

			userEntity.setMobileNumberConfirmationCode(null);
			userEntity.markMobileNumberConfirmed();

			logger.info("User with mobile number '{}' and ID '{}' successfully confirmed their mobile number",
					userEntity.getMobileNumber(), userEntity.getId());
		});
		return createUserDtoWithPrivateData(updatedUserEntity);
	}

	@Transactional
	public void resendMobileNumberConfirmationCode(UUID userId)
	{
		updateUser(userId, userEntity -> {
			logger.info("User with mobile number '{}' and ID '{}' requests to resend the mobile number confirmation code",
					userEntity.getMobileNumber(), userEntity.getId());
			ConfirmationCode confirmationCode = userUpdateService.createConfirmationCode();
			userEntity.setMobileNumberConfirmationCode(confirmationCode);
			sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
					SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
		});
	}

	@Transactional
	UserDto addUserCreatedOnBuddyRequest(UserDto buddyUserResource)
	{
		return userAdditionService.addUserCreatedOnBuddyRequest(buddyUserResource);
	}

	@Transactional
	public UserDto updateUser(UUID id, UserDto user)
	{
		return userUpdateService.updateUser(id, user);
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
		return userUpdateService.updateUser(id, updateAction);
	}

	@Transactional
	public UserDto updateUserPhoto(UUID id, Optional<UUID> userPhotoId)
	{
		return userUpdateService.updateUserPhoto(id, userPhotoId);
	}

	@Transactional
	public UserDto updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDto user)
	{
		return userUpdateService.updateUserCreatedOnBuddyRequest(id, tempPassword, user);
	}

	/**
	 * Deletes the specified user, while holding a write-lock on the user.
	 * 
	 * @param id The ID of the user to delete
	 * @param message the message to communicate to buddies
	 */
	@Transactional
	public void deleteUser(UUID id, Optional<String> message)
	{
		userRetrievalService.withLockOnUser(id, userEntity -> userDeletionService.deleteUser(userEntity, message));
	}

	@Transactional
	public void addBuddy(UserDto user, BuddyDto buddy)
	{
		userUpdateService.addBuddy(user, buddy);
	}

	public String generatePassword()
	{
		return CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength());
	}

	static User findUserByMobileNumber(String mobileNumber)
	{
		return UserRetrievalService.findUserByMobileNumber(mobileNumber);
	}

	public static String getPassword(Optional<String> password)
	{
		return UserRetrievalService.getPassword(password);
	}

	/**
	 * This method returns a user entity. The passed on Id is checked whether or not it is set. it also checks that the return
	 * value is always the user entity. If not an exception is thrown. The entity is fetched without lock.
	 * 
	 * @param id the ID of the user
	 * @return The user entity (never null)
	 */
	@Transactional
	public User getUserEntityById(UUID id)
	{
		return userRetrievalService.getUserEntityById(id);
	}

	public UserDto getUserByMobileNumber(String mobileNumber)
	{
		return userRetrievalService.getUserByMobileNumber(mobileNumber);
	}

	/**
	 * This method returns a validated user entity. A validated user means a user with a confirmed mobile number.
	 * 
	 * @param id The id of the user.
	 * @return The validated user entity. An exception is thrown is something is missing.
	 */
	@Transactional
	public User getValidatedUserById(UUID id)
	{
		return userRetrievalService.getValidatedUserById(id);
	}

	@Transactional
	public UUID getUserAnonymizedId(UUID userId)
	{
		return userRetrievalService.getUserAnonymizedId(userId);
	}

	public void assertValidatedUser(User user)
	{
		userRetrievalService.assertValidatedUser(user);
	}

	public String generateConfirmationCode()
	{
		return userUpdateService.generateConfirmationCode();
	}

	public void sendConfirmationCodeTextMessage(String mobileNumber, ConfirmationCode confirmationCode, SmsTemplate template)
	{
		userUpdateService.sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, template);
	}

	void assertValidUserFields(UserDto user, UserService.UserPurpose purpose)
	{
		UserRetrievalService.assertValidUserFields(user, purpose);
	}

	public void registerFailedAttempt(User userEntity, ConfirmationCode confirmationCode)
	{
		userAdditionService.registerFailedAttempt(userEntity, confirmationCode);
	}

	public void assertValidMobileNumber(String mobileNumber)
	{
		UserRetrievalService.assertValidMobileNumber(mobileNumber);
	}

	public void assertValidEmailAddress(String emailAddress)
	{
		UserRetrievalService.assertValidEmailAddress(emailAddress);
	}
}
