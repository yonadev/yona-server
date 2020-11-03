/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.util.HibernateHelperService;
import nu.yona.server.util.Require;

@Service
public class UserLookupService
{
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

	@Autowired(required = false)
	private UserAssertionService userAssertionService;

	@Autowired(required = false)
	private HibernateHelperService hibernateHelperService;

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
	public UserDto getUserWithoutPrivateData(UUID id)
	{
		return UserDto.createInstanceWithoutPrivateData(getUserEntityById(id));
	}

	@Transactional
	public UserDto getUser(UUID id)
	{
		return getUser(id, u -> { // Nothing required here
		});
	}

	@Transactional
	public UserDto getPrivateUser(UUID id, boolean isCreatedOnBuddyRequest)
	{
		return getUser(id, u -> assertCreatedOnBuddyRequestStatus(u, isCreatedOnBuddyRequest));
	}

	private UserDto getUser(UUID id, Consumer<User> userStatusAsserter)
	{
		User user = getUserEntityById(id);
		userStatusAsserter.accept(user);
		return createUserDto(user);
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
		return createUserDto(getPrivateValidatedUserEntity(id));
	}

	@Transactional
	public User getPrivateValidatedUserEntity(UUID id)
	{
		return getValidatedUserById(id);
	}

	public UserDto createUserDto(User user)
	{
		return UserDto.createInstance(user, userAnonymizedService.getUserAnonymized(user.getUserAnonymizedId()),
				buddyService.getBuddyDtos(user.getBuddies()));
	}

	/**
	 * Locks the user entity with the given ID for update, thus preventing concurrent updates on that user. To prevent from using
	 * stale data that was retrieved before any previous update completed, the Hibernate session is cleared. This operation fails
	 * if the session is dirty. Callers should obtain the lock on the user entity prior to any checks or updates. Note that this
	 * operation is idempotent: if the user was already locked during this session, that user will be returned. The session won't
	 * be cleared.
	 *
	 * @param userId The ID of the user to lock
	 * @return The locked user entity
	 */
	@Transactional
	public User lockUserForUpdate(UUID userId)
	{
		return getUserEntityByIdWithUpdateLock(userId);
	}

	<T> T withLockOnUser(UUID userId, Function<User, T> action)
	{
		User user = getUserEntityByIdWithUpdateLock(userId);

		return action.apply(user);
	}

	<T> Optional<T> withLockOnUserIfExisting(UUID userId, Function<User, T> action)
	{
		return getUserEntityByIdWithUpdateLockIfExisting(userId).map(action);
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
		return getUserEntityByIdIfExisting(id).orElseThrow(() -> UserServiceException.notFoundById(id));
	}

	public Optional<User> getUserEntityByIdIfExisting(UUID id)
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
		return getUserEntityByIdWithUpdateLockIfExisting(id).orElseThrow(() -> UserServiceException.notFoundById(id));
	}

	/**
	 * This method returns a user entity, if it exists. The passed on Id is checked whether or not it is set. A pessimistic
	 * database write lock is claimed when fetching the entity.
	 *
	 * @param id the ID of the user
	 * @return an {@code Optional} describing the user, or an empty {@code Optional} if a user with this ID cannot be found
	 */
	private Optional<User> getUserEntityByIdWithUpdateLockIfExisting(UUID id)
	{
		return getUserEntityById(id, LockModeType.PESSIMISTIC_WRITE);
	}

	private Optional<User> getUserEntityById(UUID id, LockModeType lockModeType)
	{
		Require.isNonNull(id, InvalidDataException::emptyUserId);

		Optional<User> entity;
		switch (lockModeType)
		{
			case NONE:
				entity = userRepository.findById(id);
				break;
			case PESSIMISTIC_WRITE:
				hibernateHelperService.clearSessionIfNotLockedYet(User.class, id);
				entity = userRepository.findByIdForUpdate(id);
				break;
			default:
				throw new IllegalArgumentException("Lock mode type " + lockModeType + " is unsupported");
		}

		return entity;
	}

	public UserDto getUserByMobileNumber(String mobileNumber)
	{
		return UserDto.createInstanceWithoutPrivateData(findUserByMobileNumber(mobileNumber));
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
		userAssertionService.assertValidatedUser(retVal);

		return retVal;
	}

	@Transactional
	public UUID getUserAnonymizedId(UUID userId)
	{
		return getUserEntityById(userId).getUserAnonymizedId();
	}
}