/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
	private UserAssertionService userAssertionService;

	@Autowired
	private UserLookupService userLookupService;

	@Autowired
	private UserUpdateService userUpdateService;

	@Autowired
	private UserAddService userAddService;

	@Autowired
	private UserDeleteService userDeleteService;

	@Transactional
	public boolean doPreparationsAndCheckCanAccessPrivateData(UUID id)
	{
		return userLookupService.doPreparationsAndCheckCanAccessPrivateData(id);
	}

	@Transactional
	public UserDto getUserWithoutPrivateData(UUID id)
	{
		return userLookupService.getUserWithoutPrivateData(id);
	}

	@Transactional
	public UserDto getUser(UUID id)
	{
		return userLookupService.getUser(id);
	}

	@Transactional
	public UserDto getUser(UUID id, boolean isCreatedOnBuddyRequest)
	{
		return userLookupService.getPrivateUser(id, isCreatedOnBuddyRequest);
	}

	@Transactional
	public UserDto getValidatedUser(UUID id)
	{
		return userLookupService.getPrivateValidatedUser(id);
	}

	@Transactional
	public User getValidatedUserEntity(UUID id)
	{
		return userLookupService.getPrivateValidatedUserEntity(id);
	}

	@Transactional
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		userUpdateService.setOverwriteUserConfirmationCode(mobileNumber);
	}

	@Transactional(dontRollbackOn = UserOverwriteConfirmationException.class)
	public UserDto addUser(UserDto user, Optional<String> overwriteUserConfirmationCode)
	{
		return userAddService.addUser(user, overwriteUserConfirmationCode);
	}

	public UserDto createUserDto(User user)
	{
		return userLookupService.createUserDto(user);
	}

	@Transactional(dontRollbackOn = MobileNumberConfirmationException.class)
	public UserDto confirmMobileNumber(UUID userId, String userProvidedConfirmationCode)
	{
		User updatedUserEntity = updateUser(userId, userEntity -> {
			ConfirmationCode confirmationCode = userEntity.getMobileNumberConfirmationCode();

			userAddService.assertValidConfirmationCode(userEntity, confirmationCode, userProvidedConfirmationCode,
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
		return createUserDto(updatedUserEntity);
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
		return userAddService.addUserCreatedOnBuddyRequest(buddyUserResource);
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

	/**
	 * Performs the given update action on the user with the specified ID (if the user exists), while holding a write-lock on
	 * the user. After the update, the entity is saved to the repository.<br/>
	 * We are using pessimistic locking because we generally do not have update concurrency, except when performing the user
	 * preparation (migration steps, processing messages, handling buddies deleted while offline, etc.). This preparation is
	 * executed during GET-requests and GET-requests can come concurrently. Optimistic locking wouldn't be an option here as that
	 * would cause the GETs to fail rather than to wait for the other one to complete.
	 *
	 * @param id The ID of the user to update
	 * @param updateAction The update action to perform
	 * @return an {@code Optional} describing the updated and saved user, or an empty {@code Optional} if a user with this ID cannot be found
	 */
	@Transactional(dontRollbackOn = { MobileNumberConfirmationException.class, UserOverwriteConfirmationException.class })
	public Optional<User> updateUserIfExisting(UUID id, Consumer<User> updateAction)
	{
		return userUpdateService.updateUserIfExisting(id, updateAction);
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
		userLookupService.withLockOnUser(id, userEntity -> userDeleteService.deleteUser(userEntity, message));
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
		return UserLookupService.findUserByMobileNumber(mobileNumber);
	}

	public static String getPassword(Optional<String> password)
	{
		return UserLookupService.getPassword(password);
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
		return userLookupService.getUserEntityById(id);
	}

	public Optional<User> getUserEntityByIdIfExisting(UUID id)
	{
		return userLookupService.getUserEntityByIdIfExisting(id);
	}

	public UserDto getUserByMobileNumber(String mobileNumber)
	{
		return userLookupService.getUserByMobileNumber(mobileNumber);
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
		return userLookupService.getValidatedUserById(id);
	}

	@Transactional
	public UUID getUserAnonymizedId(UUID userId)
	{
		return userLookupService.getUserAnonymizedId(userId);
	}

	public void assertValidatedUser(User user)
	{
		userAssertionService.assertValidatedUser(user);
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
		UserAssertionService.assertValidUserFields(user, purpose);
	}

	public void registerFailedAttempt(User userEntity, ConfirmationCode confirmationCode)
	{
		userAddService.registerFailedAttempt(userEntity, confirmationCode);
	}

	public void assertValidMobileNumber(String mobileNumber)
	{
		UserAssertionService.assertValidMobileNumber(mobileNumber);
	}

	public void assertValidEmailAddress(String emailAddress)
	{
		UserAssertionService.assertValidEmailAddress(emailAddress);
	}
}
