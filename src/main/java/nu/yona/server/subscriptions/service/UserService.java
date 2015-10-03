/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.rest.Constants;
import nu.yona.server.subscriptions.entities.User;

@Component
public class UserService {
	// TODO: Do we need this? It implies a security vulnerability
	public UserDTO getUser(String emailAddress, String mobileNumber) {

		if (emailAddress != null && mobileNumber != null) {
			throw new IllegalArgumentException("Either emailAddress or mobileNumber must be non-null");
		}
		if (emailAddress == null && mobileNumber == null) {
			throw new IllegalArgumentException("One of emailAddress and mobileNumber must be null");
		}

		User userEntity = findUserByEmailAddressOrMobileNumber(emailAddress, mobileNumber);
		return UserDTO.createFullyInitializedInstance(userEntity);
	}

	public UserDTO getPublicUser(long id) {
		return UserDTO.createMinimallyInitializedInstance(getEntityByID(id));
	}

	public UserDTO getPrivateUser(long id) {
		return UserDTO.createFullyInitializedInstance(getEntityByID(id));
	}

	public UserDTO addUser(UserDTO userResource) {
		UserDTO savedUser = UserDTO
				.createFullyInitializedInstance(User.getRepository().save(userResource.createUserEntity()));

		return savedUser;
	}

	public UserDTO updateUser(long id, UserDTO userResource) {
		User originalUserEntity = getEntityByID(id);
		UserDTO savedUser;
		if (originalUserEntity.isCreatedOnBuddyRequest()) {
			savedUser = handleUserSignupUponBuddyRequest(userResource.getPassword(), userResource, originalUserEntity);
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

	private UpdatedEntities updateUserWithTempPassword(UserDTO userResource, User originalUserEntity) {
		try (CryptoSession cryptoSession = CryptoSession.start(userResource.getPassword())) {
			User updatedUserEntity = userResource.updateUser(originalUserEntity);
			MessageSource touchedNameMessageSource = touchMessageSource(updatedUserEntity.getNamedMessageSource());
			MessageSource touchedAnonymousMessageSource = touchMessageSource(
					updatedUserEntity.getAnonymousMessageSource());
			return new UpdatedEntities(updatedUserEntity, touchedNameMessageSource, touchedAnonymousMessageSource);
		}
	}

	private MessageSource touchMessageSource(MessageSource messageSource) {
		messageSource.touch();
		return messageSource;
	}

	private UserDTO saveUserWithDevicePassword(String password, UpdatedEntities updatedEntities) {
		UserDTO savedUser;
		try (CryptoSession cryptoSession = CryptoSession.start(password)) {
			MessageSource.getRepository().save(updatedEntities.namedMessageSource);
			MessageSource.getRepository().save(updatedEntities.anonymousMessageSource);
			savedUser = UserDTO.createFullyInitializedInstance(User.getRepository().save(updatedEntities.userEntity));
		}
		return savedUser;
	}

	private UserDTO handleUserUpdate(UserDTO userResource, User originalUserEntity) {
		return UserDTO
				.createFullyInitializedInstance(User.getRepository().save(userResource.updateUser(originalUserEntity)));
	}

	public void deleteUser(Optional<String> password, long id) {

		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password))) {
			User.getRepository().delete(id);
		}
	}

	public User getEntityByID(long id) {
		User entity = User.getRepository().findOne(id);
		if (entity == null) {
			throw UserNotFoundException.notFoundByID(id);
		}
		return entity;
	}

	public void verifyExistence(long id) {
		getEntityByID(id);
	}

	public static String getPassword(Optional<String> password) {
		return password.orElseThrow(() -> new YonaException("Missing '" + Constants.PASSWORD_HEADER + "' header"));
	}
}
