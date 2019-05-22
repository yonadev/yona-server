/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.util.Require;

@Service
public class UserLookupService
{
	/**
	 * Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers
	 */
	private static final Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	/**
	 * Holds the regex to validate a valid email address. Match the pattern a@b.c
	 */
	private static final Pattern REGEX_EMAIL = Pattern.compile("^[A-Z0-9._-]+@[A-Z0-9.-]+\\.[A-Z0-9.-]+$",
			Pattern.CASE_INSENSITIVE);

	@Autowired(required = false)
	private UserRepository userRepository;

	@Autowired(required = false)
	private BuddyService buddyService;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private MessageService messageService;

	@Autowired(required = false)
	private PrivateUserDataMigrationService privateUserDataMigrationService;

	@Transactional
	public boolean doPreparationsAndCheckCanAccessPrivateData(UUID id)
	{
		// We add a lock here to prevent concurrent user updates. The lock is per user, so concurrency is not an issue.
		return withLockOnUser(id, user -> {
			if (!user.canAccessPrivateData())
			{
				return false;
			}
			boolean updated = doPreparations(user);
			if (updated)
			{
				// The private data is accessible and might be updated, including the UserAnonymized
				// Let the UserAnonymizedService save that to the repository and cache it
				userAnonymizedService.updateUserAnonymized(user.getAnonymized());
				userRepository.save(user);
			}
			return true;
		});
	}

	private boolean doPreparations(User user)
	{
		boolean updated = false;
		updated |= doPreparationBuddiesRemovedWhileOffline(user);
		updated |= doPreparationPrivateDataMigration(user);
		updated |= doPreparationForMessages(user);
		return updated;
	}

	private boolean doPreparationBuddiesRemovedWhileOffline(User user)
	{
		Set<Buddy> buddiesOfRemovedUsers = user.getBuddiesRelatedToRemovedUsers();
		if (!buddiesOfRemovedUsers.isEmpty())
		{
			buddiesOfRemovedUsers.stream().forEach(b -> buddyService.removeBuddyInfoForRemovedUser(user, b));
			return true;
		}
		return false;
	}

	private boolean doPreparationPrivateDataMigration(User user)
	{
		if (!privateUserDataMigrationService.isUpToDate(user))
		{
			privateUserDataMigrationService.upgrade(user);
			return true;
		}
		return false;
	}

	private boolean doPreparationForMessages(User user)
	{
		return messageService.prepareMessageCollection(user);
	}

	@Transactional
	public UserDto getPublicUser(UUID id)
	{
		return UserDto.createInstance(getUserEntityById(id));
	}

	@Transactional
	public UserDto getPrivateUser(UUID id)
	{
		return getPrivateUser(id, u -> { // Nothing required here
		});
	}

	@Transactional
	public UserDto getPrivateUser(UUID id, boolean isCreatedOnBuddyRequest)
	{
		return getPrivateUser(id, u -> assertCreatedOnBuddyRequestStatus(u, isCreatedOnBuddyRequest));
	}

	private UserDto getPrivateUser(UUID id, Consumer<User> userStatusAsserter)
	{
		User user = getUserEntityById(id);
		userStatusAsserter.accept(user);
		return createUserDtoWithPrivateData(user);
	}

	private void assertCreatedOnBuddyRequestStatus(User user, boolean isCreatedOnBuddyRequest)
	{
		if (isCreatedOnBuddyRequest == user.isCreatedOnBuddyRequest())
		{
			return;
		}

		// security check: the app should not mix the temp password and the regular one
		if (user.isCreatedOnBuddyRequest())
		{
			throw UserServiceException.userCreatedOnBuddyRequest(user.getId());

		}
		else
		{
			throw UserServiceException.userNotCreatedOnBuddyRequest(user.getId());
		}
	}

	@Transactional
	public UserDto getPrivateValidatedUser(UUID id)
	{
		return createUserDtoWithPrivateData(getPrivateValidatedUserEntity(id));
	}

	@Transactional
	public User getPrivateValidatedUserEntity(UUID id)
	{
		return getValidatedUserById(id);
	}

	public UserDto createUserDtoWithPrivateData(User user)
	{
		return UserDto.createInstanceWithPrivateData(user, buddyService.getBuddyDtos(user.getBuddies()));
	}

	<T> T withLockOnUser(UUID userId, Function<User, T> action)
	{
		User user = getUserEntityByIdWithUpdateLock(userId);

		return action.apply(user);
	}

	static User findUserByMobileNumber(String mobileNumber)
	{
		User userEntity = User.getRepository().findByMobileNumber(mobileNumber);
		Require.isNonNull(userEntity, () -> UserServiceException.notFoundByMobileNumber(mobileNumber));
		return userEntity;
	}

	public static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(UserServiceException::missingPasswordHeader);
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
		return getUserEntityById(id, LockModeType.NONE);
	}

	/**
	 * This method returns a user entity. The passed on Id is checked whether or not it is set. it also checks that the return
	 * value is always the user entity. If not an exception is thrown. A pessimistic database write lock is claimed when fetching
	 * the entity.
	 * 
	 * @param id the ID of the user
	 * @return The user entity (never null)
	 */
	private User getUserEntityByIdWithUpdateLock(UUID id)
	{
		return getUserEntityById(id, LockModeType.PESSIMISTIC_WRITE);
	}

	private User getUserEntityById(UUID id, LockModeType lockModeType)
	{
		Require.isNonNull(id, InvalidDataException::emptyUserId);

		User entity;
		switch (lockModeType)
		{
			case NONE:
				entity = userRepository.findById(id).orElseThrow(() -> UserServiceException.notFoundById(id));
				break;
			case PESSIMISTIC_WRITE:
				entity = userRepository.findByIdForUpdate(id);
				break;
			default:
				throw new IllegalArgumentException("Lock mode type " + lockModeType + " is unsupported");
		}

		Require.isNonNull(entity, () -> UserServiceException.notFoundById(id));

		return entity;
	}

	public UserDto getUserByMobileNumber(String mobileNumber)
	{
		return UserDto.createInstance(findUserByMobileNumber(mobileNumber));
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
		User retVal = getUserEntityById(id);
		assertValidatedUser(retVal);

		return retVal;
	}

	@Transactional
	public UUID getUserAnonymizedId(UUID userId)
	{
		return getUserEntityById(userId).getUserAnonymizedId();
	}

	public void assertValidatedUser(User user)
	{
		user.assertMobileNumberConfirmed();
	}

	static void assertValidUserFields(UserDto user, UserService.UserPurpose purpose)
	{
		Require.that(StringUtils.isNotBlank(user.getOwnPrivateData().getFirstName()), InvalidDataException::blankFirstName);
		Require.that(StringUtils.isNotBlank(user.getOwnPrivateData().getLastName()), InvalidDataException::blankLastName);
		Require.that(!(purpose == UserService.UserPurpose.USER && StringUtils.isBlank(user.getOwnPrivateData().getNickname())),
				InvalidDataException::blankNickname);

		Require.that(StringUtils.isNotBlank(user.getMobileNumber()), InvalidDataException::blankMobileNumber);

		assertValidMobileNumber(user.getMobileNumber());

		if (purpose == UserService.UserPurpose.BUDDY)
		{
			Require.that(StringUtils.isNotBlank(user.getEmailAddress()), InvalidDataException::blankEmailAddress);
			assertValidEmailAddress(user.getEmailAddress());

			Require.that(user.getOwnPrivateData().getGoals().orElse(Collections.emptySet()).isEmpty(),
					InvalidDataException::goalsNotSupported);
		}
		else
		{
			Require.that(StringUtils.isBlank(user.getEmailAddress()), InvalidDataException::excessEmailAddress);
		}
	}

	public static void assertValidMobileNumber(String mobileNumber)
	{
		Require.that(REGEX_PHONE.matcher(mobileNumber).matches(), () -> InvalidDataException.invalidMobileNumber(mobileNumber));
		assertNumberIsMobile(mobileNumber);
	}

	private static void assertNumberIsMobile(String mobileNumber)
	{
		try
		{
			PhoneNumberUtil util = PhoneNumberUtil.getInstance();
			PhoneNumberType numberType = util.getNumberType(util.parse(mobileNumber, null));
			if ((numberType != PhoneNumberType.MOBILE) && (numberType != PhoneNumberType.FIXED_LINE_OR_MOBILE))
			{
				throw InvalidDataException.notAMobileNumber(mobileNumber, numberType.toString());
			}
		}
		catch (NumberParseException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static void assertValidEmailAddress(String emailAddress)
	{
		Require.that(REGEX_EMAIL.matcher(emailAddress).matches(), () -> InvalidDataException.invalidEmailAddress(emailAddress));
	}

	void assertUserDoesNotExist(String mobileNumber)
	{
		User existingUser = userRepository.findByMobileNumber(mobileNumber);
		if (existingUser == null)
		{
			return;
		}

		if (existingUser.isCreatedOnBuddyRequest())
		{
			throw UserServiceException.userExistsCreatedOnBuddyRequest(mobileNumber);
		}
		throw UserServiceException.userExists(mobileNumber);
	}
}