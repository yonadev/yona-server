/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.transaction.Transactional;

import nu.yona.server.crypto.Constants;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.NewDeviceRequest;
import nu.yona.server.subscriptions.entities.User;

import org.springframework.stereotype.Service;

@Service
public class UserService
{
	// TODO: Do we need this? Currently unused.
	@Transactional
	public UserDTO getUser(String emailAddress, String mobileNumber)
	{

		if (emailAddress != null && mobileNumber != null)
		{
			throw new IllegalArgumentException("Either emailAddress or mobileNumber must be non-null");
		}
		if (emailAddress == null && mobileNumber == null)
		{
			throw new IllegalArgumentException("One of emailAddress and mobileNumber must be null");
		}

		User userEntity = findUserByEmailAddressOrMobileNumber(emailAddress, mobileNumber);
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
		UserDTO savedUser = UserDTO.createInstanceWithPrivateData(User.getRepository().save(userResource.createUserEntity()));

		return savedUser;
	}

	@Transactional
	public Set<String> getDevicesForUser(UUID forUserID)
	{
		User userEntity = getEntityByID(forUserID);
		return userEntity.getDeviceNames();
	}

	@Transactional
	public Set<String> addDeviceForUser(UUID forUserID, String deviceName)
	{
		User originalUserEntity = getEntityByID(forUserID);
		Set<String> deviceNames = new TreeSet<String>(originalUserEntity.getDeviceNames());
		deviceNames.add(deviceName);
		originalUserEntity.setDeviceNames(deviceNames);
		User updatedUserEntity = User.getRepository().save(originalUserEntity);
		return updatedUserEntity.getDeviceNames();
	}

	@Transactional
	public Set<String> deleteDeviceForUser(UUID forUserID, String deviceName)
	{
		User originalUserEntity = getEntityByID(forUserID);
		Set<String> deviceNames = new TreeSet<String>(originalUserEntity.getDeviceNames());
		deviceNames.remove(deviceName);
		originalUserEntity.setDeviceNames(deviceNames);
		User updatedUserEntity = User.getRepository().save(originalUserEntity);
		return updatedUserEntity.getDeviceNames();
	}

	@Transactional
	public UserDTO updateUser(UUID id, UserDTO userResource)
	{
		User originalUserEntity = getEntityByID(id);
		UserDTO savedUser;
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// TODO: the password needs to come from the URL, not from the
			// payload
			savedUser = handleUserSignupUponBuddyRequest("TODO", userResource, originalUserEntity);
		}
		else
		{
			savedUser = handleUserUpdate(userResource, originalUserEntity);
		}
		return savedUser;
	}

	static User findUserByEmailAddressOrMobileNumber(String emailAddress, String mobileNumber)
	{
		User userEntity;
		if (emailAddress == null)
		{
			userEntity = User.getRepository().findByMobileNumber(mobileNumber);
			if (userEntity == null)
			{
				throw UserNotFoundException.notFoundByMobileNumber(mobileNumber);
			}
		}
		else
		{
			userEntity = User.getRepository().findByEmailAddress(emailAddress);
			if (userEntity == null)
			{
				throw UserNotFoundException.notFoundByEmailAddress(emailAddress);
			}
		}
		return userEntity;
	}

	private UserDTO handleUserSignupUponBuddyRequest(String password, UserDTO userResource, User originalUserEntity)
	{
		UpdatedEntities updatedEntities = updateUserWithTempPassword(userResource, originalUserEntity);
		return saveUserWithDevicePassword(password, updatedEntities);
	}

	static class UpdatedEntities
	{
		final User userEntity;
		final MessageSource namedMessageSource;
		final MessageSource anonymousMessageSource;

		UpdatedEntities(User userEntity, MessageSource namedMessageSource, MessageSource anonymousMessageSource)
		{
			this.userEntity = userEntity;
			this.namedMessageSource = namedMessageSource;
			this.anonymousMessageSource = anonymousMessageSource;
		}
	}

	private UpdatedEntities updateUserWithTempPassword(UserDTO userDTO, User originalUserEntity)
	{
		// TODO: the password needs to come from the URL, not from the
		// payload
		return CryptoSession.execute(Optional.of("TODO"), () -> canAccessPrivateData(userDTO.getID()), () -> {
			User updatedUserEntity = userDTO.updateUser(originalUserEntity);
			MessageSource touchedNameMessageSource = touchMessageSource(updatedUserEntity.getNamedMessageSource());
			MessageSource touchedAnonymousMessageSource = touchMessageSource(updatedUserEntity.getAnonymousMessageSource());
			return new UpdatedEntities(updatedUserEntity, touchedNameMessageSource, touchedAnonymousMessageSource);
		});
	}

	private MessageSource touchMessageSource(MessageSource messageSource)
	{
		messageSource.touch();
		return messageSource;
	}

	private UserDTO saveUserWithDevicePassword(String password, UpdatedEntities updatedEntities)
	{
		return CryptoSession.execute(Optional.of(password), () -> canAccessPrivateData(updatedEntities.userEntity.getID()),
				() -> {
					UserDTO savedUser;
					MessageSource.getRepository().save(updatedEntities.namedMessageSource);
					MessageSource.getRepository().save(updatedEntities.anonymousMessageSource);
					savedUser = UserDTO.createInstanceWithPrivateData(User.getRepository().save(updatedEntities.userEntity));
					return savedUser;
				});
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

	@Transactional
	public NewDeviceRequestDTO setNewDeviceRequestForUser(UUID id, String userPassword, String userSecret)
	{
		User userEntity = getEntityByID(id);
		Date expirationDateTime = getNewDeviceRequestExpirationDateTime();
		NewDeviceRequest newDeviceRequestEntity = NewDeviceRequest.createInstance(userPassword, expirationDateTime);
		newDeviceRequestEntity.encryptUserPassword(userSecret);
		userEntity.setNewDeviceRequest(newDeviceRequestEntity);
		return NewDeviceRequestDTO.createInstance(User.getRepository().save(userEntity).getNewDeviceRequest());
	}

	private Date getNewDeviceRequestExpirationDateTime()
	{
		Date now = new Date();
		Calendar c = Calendar.getInstance();
		c.setTime(now);
		c.add(Calendar.DATE, 1);
		Date expirationDateTime = c.getTime();
		return expirationDateTime;
	}

	@Transactional
	public NewDeviceRequestDTO getNewDeviceRequestForUser(UUID id, String userSecret)
	{
		if (userSecret == null || userSecret.isEmpty())
		{
			User userEntity = getEntityByID(id);
			return NewDeviceRequestDTO.createInstance(userEntity.getNewDeviceRequest());
		}
		else
		{
			return getNewDeviceRequestWithPasswordForUser(id, userSecret);
		}
	}

	private NewDeviceRequestDTO getNewDeviceRequestWithPasswordForUser(UUID id, String userSecret)
	{
		User userEntity = getEntityByID(id);
		NewDeviceRequest newDeviceRequestEntity = userEntity.getNewDeviceRequest();
		if (newDeviceRequestEntity == null)
		{
			throw new NewDeviceRequestNotPresentException(id);
		}
		newDeviceRequestEntity.decryptUserPassword(userSecret);
		return NewDeviceRequestDTO.createInstanceWithPassword(newDeviceRequestEntity);
	}

	@Transactional
	public void clearNewDeviceRequestForUser(UUID id)
	{
		User userEntity = getEntityByID(id);
		NewDeviceRequest existingNewDeviceRequestEntity = userEntity.getNewDeviceRequest();
		if (existingNewDeviceRequestEntity != null)
		{
			userEntity.setNewDeviceRequest(null);
			User.getRepository().save(userEntity);
		}
	}
}
