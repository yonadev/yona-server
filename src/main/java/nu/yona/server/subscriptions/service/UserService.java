/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.rest.Constants;
import nu.yona.server.subscriptions.entities.User;

@Service
public class UserService {
	// TODO: Do we need this? Currently unused.
	public UserDTO getUser(String emailAddress, String mobileNumber) {

		if (emailAddress != null && mobileNumber != null) {
			throw new IllegalArgumentException("Either emailAddress or mobileNumber must be non-null");
		}
		if (emailAddress == null && mobileNumber == null) {
			throw new IllegalArgumentException("One of emailAddress and mobileNumber must be null");
		}

		User userEntity = findUserByEmailAddressOrMobileNumber(emailAddress, mobileNumber);
		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	public boolean canAccessPrivateData(UUID id) {
		return getEntityByID(id).canAccessPrivateData();
	}

	public UserDTO getPublicUser(UUID id) {
		return UserDTO.createInstance(getEntityByID(id));
	}

	public UserDTO getPrivateUser(UUID id) {
		return UserDTO.createInstanceWithPrivateData(getEntityByID(id));
	}

	public UserDTO addUser(UserDTO userResource) {
		UserDTO savedUser = UserDTO
				.createInstanceWithPrivateData(User.getRepository().save(userResource.createUserEntity()));

		return savedUser;
	}

	public UserDTO updateUser(UUID id, UserDTO userResource) {
		User originalUserEntity = getEntityByID(id);
		UserDTO savedUser;
		if (originalUserEntity.isCreatedOnBuddyRequest()) {
			// TODO: the password needs to come from the URL, not from the
			// payload
			savedUser = handleUserSignupUponBuddyRequest("TODO", userResource, originalUserEntity);
		} else {
			savedUser = handleUserUpdate(userResource, originalUserEntity);
		}
		return savedUser;
	}

	static User findUserByEmailAddressOrMobileNumber(String emailAddress, String mobileNumber) {
		User userEntity;
		if (emailAddress == null) {
			userEntity = User.getRepository().findByMobileNumber(mobileNumber);
			if (userEntity == null) {
				throw UserNotFoundException.notFoundByMobileNumber(mobileNumber);
			}
		} else {
			userEntity = User.getRepository().findByEmailAddress(emailAddress);
			if (userEntity == null) {
				throw UserNotFoundException.notFoundByEmailAddress(emailAddress);
			}
		}
		return userEntity;
	}

	private UserDTO handleUserSignupUponBuddyRequest(String password, UserDTO userResource, User originalUserEntity) {
		UpdatedEntities updatedEntities = updateUserWithTempPassword(userResource, originalUserEntity);
		return saveUserWithDevicePassword(password, updatedEntities);
	}

	static class UpdatedEntities {
		final User userEntity;
		final MessageSource namedMessageSource;
		final MessageSource anonymousMessageSource;

		UpdatedEntities(User userEntity, MessageSource namedMessageSource, MessageSource anonymousMessageSource) {
			this.userEntity = userEntity;
			this.namedMessageSource = namedMessageSource;
			this.anonymousMessageSource = anonymousMessageSource;
		}
	}

	private UpdatedEntities updateUserWithTempPassword(UserDTO userDTO, User originalUserEntity) {
		// TODO: the password needs to come from the URL, not from the
		// payload
		return CryptoSession.execute(Optional.of("TODO"), () -> canAccessPrivateData(userDTO.getID()), () -> {
			User updatedUserEntity = userDTO.updateUser(originalUserEntity);
			MessageSource touchedNameMessageSource = touchMessageSource(updatedUserEntity.getNamedMessageSource());
			MessageSource touchedAnonymousMessageSource = touchMessageSource(
					updatedUserEntity.getAnonymousMessageSource());
			return new UpdatedEntities(updatedUserEntity, touchedNameMessageSource, touchedAnonymousMessageSource);
		});
	}

	private MessageSource touchMessageSource(MessageSource messageSource) {
		messageSource.touch();
		return messageSource;
	}

	private UserDTO saveUserWithDevicePassword(String password, UpdatedEntities updatedEntities) {
		return CryptoSession.execute(Optional.of(password),
				() -> canAccessPrivateData(updatedEntities.userEntity.getID()), () -> {
					UserDTO savedUser;
					MessageSource.getRepository().save(updatedEntities.namedMessageSource);
					MessageSource.getRepository().save(updatedEntities.anonymousMessageSource);
					savedUser = UserDTO
							.createInstanceWithPrivateData(User.getRepository().save(updatedEntities.userEntity));
					return savedUser;
				});
	}

	private UserDTO handleUserUpdate(UserDTO userResource, User originalUserEntity) {
		return UserDTO
				.createInstanceWithPrivateData(User.getRepository().save(userResource.updateUser(originalUserEntity)));
	}

	public void deleteUser(Optional<String> password, UUID id) {

		User.getRepository().delete(id);
	}

	public User getEntityByID(UUID id) {
		User entity = User.getRepository().findOne(id);
		if (entity == null) {
			throw UserNotFoundException.notFoundByID(id);
		}
		return entity;
	}

	public static String getPassword(Optional<String> password) {
		return password.orElseThrow(() -> new YonaException("Missing '" + Constants.PASSWORD_HEADER + "' header"));
	}
}
