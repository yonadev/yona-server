/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import nu.yona.server.crypto.Constants;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService
{
	/** Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers */
	private static Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	// TODO: Do we need this? Currently unused.
	@Transactional
	public UserDTO getUser(String mobileNumber)
	{
		if (mobileNumber == null)
		{
			throw new IllegalArgumentException("mobileNumber cannot be null");
		}

		User userEntity = findUserByMobileNumber(mobileNumber);
		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	@Transactional
	public boolean canAccessPrivateData(UUID id)
	{
		return getEntityByID(id).canAccessPrivateData();
	}

	@Transactional
	public UserDTO getPublicUser(UUID id)
	{
		return UserDTO.createInstance(getEntityByID(id));
	}

	@Transactional
	public UserDTO getPrivateUser(UUID id)
	{
		return UserDTO.createInstanceWithPrivateData(getEntityByID(id));
	}

	@Transactional
	public UserDTO addUser(UserDTO userResource)
	{
		validateUserFields(userResource);

		User userEntity = userResource.createUserEntity();
		userEntity = User.getRepository().save(userEntity);

		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	@Autowired
	UserServiceTempEncryptionContextExecutor tempEncryptionContextExecutor;

	public User addUserCreatedOnBuddyRequest(UserDTO buddyUserResource, String tempPassword)
	{
		return CryptoSession.execute(Optional.of(tempPassword), null,
				() -> tempEncryptionContextExecutor.addUserCreatedOnBuddyRequestFlush(buddyUserResource));
	}

	@Transactional
	public UserDTO updateUser(UUID id, UserDTO userResource)
	{
		User originalUserEntity = getEntityByID(id);
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			throw new YonaException("User is created on buddy request, use other method");
		}
		return handleUserUpdate(userResource, originalUserEntity);
	}

	static User findUserByMobileNumber(String mobileNumber)
	{
		User userEntity;
		userEntity = User.getRepository().findByMobileNumber(mobileNumber);
		if (userEntity == null)
		{
			throw UserNotFoundException.notFoundByMobileNumber(mobileNumber);
		}
		return userEntity;
	}

	@Transactional
	public UserDTO updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDTO userResource)
	{
		User originalUserEntity = getEntityByID(id);
		if (!originalUserEntity.isCreatedOnBuddyRequest())
		{
			throw new YonaException("User is not created on buddy request");
		}
		UserEncryptedEntitySet retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		return saveUserEncryptedDataWithNewPassword(retrievedEntitySet, userResource);
	}

	static class UserEncryptedEntitySet
	{
		final User userEntity;
		final UserAnonymized userAnonymizedEntity;
		final MessageSource namedMessageSource;
		final MessageSource anonymousMessageSource;

		UserEncryptedEntitySet(User userEntity, UserAnonymized userAnonymizedEntity, MessageSource namedMessageSource,
				MessageSource anonymousMessageSource)
		{
			this.userEntity = userEntity;
			this.userAnonymizedEntity = userAnonymizedEntity;
			this.namedMessageSource = namedMessageSource;
			this.anonymousMessageSource = anonymousMessageSource;
		}

		public void loadLazyEncryptedData()
		{
			// load encrypted data fully, also from lazy relations
			// see architecture overview for which classes contain encrypted data
			// the relation to user private is currently the only lazy relation
			// (this could also be achieved with very complex reflection)
			this.userEntity.getUserPrivate();
		}
	}

	private UserEncryptedEntitySet retrieveUserEncryptedData(User originalUserEntity, String password)
	{
		return CryptoSession.execute(Optional.of(password), () -> canAccessPrivateData(originalUserEntity.getID()),
				() -> tempEncryptionContextExecutor.retrieveUserEncryptedDataFlush(originalUserEntity));
	}

	private UserDTO saveUserEncryptedDataWithNewPassword(UserEncryptedEntitySet retrievedEntitySet, UserDTO userResource)
	{
		// touch and save all user related data containing encryption
		// see architecture overview for which classes contain encrypted data
		// (this could also be achieved with very complex reflection)
		retrievedEntitySet.userEntity.getUserPrivate().getBuddies().forEach(buddy -> Buddy.getRepository().save(buddy.touch()));
		MessageSource.getRepository().save(retrievedEntitySet.namedMessageSource.touch());
		MessageSource.getRepository().save(retrievedEntitySet.anonymousMessageSource.touch());
		userResource.updateUser(retrievedEntitySet.userEntity);
		retrievedEntitySet.userEntity.getUserPrivate().touch();
		return UserDTO.createInstanceWithPrivateData(User.getRepository().save(retrievedEntitySet.userEntity));
	}

	private UserDTO handleUserUpdate(UserDTO userResource, User originalUserEntity)
	{
		return UserDTO.createInstanceWithPrivateData(User.getRepository().save(userResource.updateUser(originalUserEntity)));
	}

	public void deleteUser(Optional<String> password, UUID id)
	{

		User.getRepository().delete(id);
	}

	private User getEntityByID(UUID id)
	{
		User entity = User.getRepository().findOne(id);
		if (entity == null)
		{
			throw UserNotFoundException.notFoundByID(id);
		}
		return entity;
	}

	private void validateUserFields(UserDTO userResource)
	{
		if (StringUtils.isBlank(userResource.getFirstName()))
		{
			throw new InvalidDataException("error.user.firstname");
		}

		if (StringUtils.isBlank(userResource.getLastName()))
		{
			throw new InvalidDataException("error.user.lastname");
		}

		if (StringUtils.isBlank(userResource.getMobileNumber()))
		{
			throw new InvalidDataException("error.user.mobile.number");
		}

		if (!REGEX_PHONE.matcher(userResource.getMobileNumber()).matches())
		{
			throw new InvalidDataException("error.user.mobile.number.invalid");
		}
	}

	public static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(() -> new YonaException("Missing '" + Constants.PASSWORD_HEADER + "' header"));
	}

	public void addBuddy(UserDTO user, BuddyDTO buddy)
	{
		User userEntity = getEntityByID(user.getID());
		Buddy buddyEntity = Buddy.getRepository().findOne(buddy.getID());
		userEntity.addBuddy(buddyEntity);
		User.getRepository().save(userEntity);
	}

	public String generateTempPassword()
	{
		return "FOO"; // TODO
	}
}
