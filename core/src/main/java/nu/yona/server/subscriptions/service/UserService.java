/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.text.MessageFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.NewDeviceRequest;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@Service
public class UserService
{
	/** Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers */
	private static Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	@Autowired
	private LDAPUserService ldapUserService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private SmsService smsService;

	@Autowired
	BuddyService buddyService;

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
	public UserDTO addUser(UserDTO user)
	{
		validateUserFields(user);

		user.getPrivateData().getVpnProfile().setVpnPassword(generatePassword());

		User userEntity = user.createUserEntity();
		userEntity
				.setConfirmationCode(CryptoUtil.getRandomDigits(yonaProperties.getSms().getMobileNumberConfirmationCodeDigits()));
		userEntity = User.getRepository().save(userEntity);
		ldapUserService.createVPNAccount(userEntity.getVPNLoginID().toString(), userEntity.getVPNPassword());

		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(userEntity);
		if (!yonaProperties.getSms().isEnabled())
		{
			userDTO.setConfirmationCode(userEntity.getConfirmationCode());
		}

		sendMobileNumberConfirmationMessage(userEntity);
		return userDTO;
	}

	private void sendMobileNumberConfirmationMessage(User userEntity)
	{
		String message = MessageFormat.format(yonaProperties.getSms().getMobileNumberConfirmationMessage(),
				userEntity.getConfirmationCode());
		smsService.send(userEntity.getMobileNumber(), message);
	}

	@Transactional
	public UserDTO confirmMobileNumber(UUID userID, String code)
	{
		User userEntity = getEntityByID(userID);

		if (userEntity.getConfirmationCode() == null)
		{
			throw MobileNumberConfirmationException.confirmationCodeNotSet();
		}

		if (!userEntity.getConfirmationCode().equals(code))
		{
			throw MobileNumberConfirmationException.confirmationCodeMismatch();
		}

		if (userEntity.isConfirmed())
		{
			throw MobileNumberConfirmationException.userCannotBeActivated();
		}

		userEntity.setConfirmationCode(null);
		userEntity.markAsConfirmed();
		User.getRepository().save(userEntity);

		return UserDTO.createInstance(userEntity);
	}

	@Autowired
	UserServiceTempEncryptionContextExecutor tempEncryptionContextExecutor;

	@Transactional
	public User addUserCreatedOnBuddyRequest(UserDTO buddyUser, String tempPassword)
	{
		UUID savedUserID = CryptoSession.execute(Optional.of(tempPassword), null,
				() -> tempEncryptionContextExecutor.addUserCreatedOnBuddyRequest(buddyUser).getID());
		return getEntityByID(savedUserID);
	}

	@Transactional
	public UserDTO updateUser(UUID id, UserDTO user)
	{
		User originalUserEntity = getEntityByID(id);
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to update a user created on buddy request with its temp password
			throw new IllegalArgumentException("User is created on buddy request, use other method");
		}
		return UserDTO.createInstanceWithPrivateData(User.getRepository().save(user.updateUser(originalUserEntity)));
	}

	static User findUserByMobileNumber(String mobileNumber)
	{
		User userEntity;
		userEntity = User.getRepository().findByMobileNumber(mobileNumber);
		if (userEntity == null)
		{
			throw UserServiceException.notFoundByMobileNumber(mobileNumber);
		}
		return userEntity;
	}

	@Transactional
	public UserDTO updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDTO userResource)
	{
		User originalUserEntity = getEntityByID(id);
		if (!originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to replace the password on an existing user
			throw new IllegalArgumentException("User is not created on buddy request");
		}
		EncryptedUserData retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		return saveUserEncryptedDataWithNewPassword(retrievedEntitySet, userResource);
	}

	private EncryptedUserData retrieveUserEncryptedData(User originalUserEntity, String password)
	{
		return CryptoSession.execute(Optional.of(password), () -> canAccessPrivateData(originalUserEntity.getID()),
				() -> tempEncryptionContextExecutor.retrieveUserEncryptedData(originalUserEntity));
	}

	private UserDTO saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDTO userResource)
	{
		// touch and save all user related data containing encryption
		// see architecture overview for which classes contain encrypted data
		// (this could also be achieved with very complex reflection)
		retrievedEntitySet.userEntity.getBuddies().forEach(buddy -> Buddy.getRepository().save(buddy.touch()));
		MessageSource.getRepository().save(retrievedEntitySet.namedMessageSource.touch());
		MessageSource.getRepository().save(retrievedEntitySet.anonymousMessageSource.touch());
		userResource.updateUser(retrievedEntitySet.userEntity);
		retrievedEntitySet.userEntity.unsetIsCreatedOnBuddyRequest();
		retrievedEntitySet.userEntity.touch();
		return UserDTO.createInstanceWithPrivateData(User.getRepository().save(retrievedEntitySet.userEntity));
	}

	@Transactional
	public void deleteUser(UUID id, Optional<String> message)
	{
		User userEntity = getEntityByID(id);

		userEntity.getBuddies().forEach(buddyEntity -> buddyService.clearMessagesAndSendDropBuddyMessage(userEntity, buddyEntity,
				message, DropBuddyReason.USER_ACCOUNT_DELETED));

		UserAnonymized userAnonymized = userEntity.getAnonymized();
		UserAnonymized.getRepository().delete(userAnonymized);
		MessageSource namedMessageSource = userEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = userEntity.getAnonymousMessageSource();
		MessageSource.getRepository().delete(anonymousMessageSource);
		MessageSource.getRepository().delete(namedMessageSource);
		User.getRepository().delete(userEntity);
	}

	private User getEntityByID(UUID id)
	{
		User entity = User.getRepository().findOne(id);
		if (entity == null)
		{
			throw UserServiceException.notFoundByID(id);
		}
		return entity;
	}

	private void validateUserFields(UserDTO userResource)
	{
		if (StringUtils.isBlank(userResource.getFirstName()))
		{
			throw InvalidDataException.blankFirstName();
		}

		if (StringUtils.isBlank(userResource.getLastName()))
		{
			throw InvalidDataException.blankLastName();
		}

		if (StringUtils.isBlank(userResource.getMobileNumber()))
		{
			throw InvalidDataException.blankMobileNumber();
		}

		if (!REGEX_PHONE.matcher(userResource.getMobileNumber()).matches())
		{
			throw InvalidDataException.invalidMobileNumber(userResource.getMobileNumber());
		}
	}

	public static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(() -> UserServiceException.missingPasswordHeader());
	}

	public void addBuddy(UserDTO user, BuddyDTO buddy)
	{
		User userEntity = getEntityByID(user.getID());
		Buddy buddyEntity = Buddy.getRepository().findOne(buddy.getID());
		userEntity.addBuddy(buddyEntity);
		User.getRepository().save(userEntity);
	}

	public String generatePassword()
	{
		return CryptoUtil.getRandomString(yonaProperties.getPasswordLength());
	}

	@Transactional
	public NewDeviceRequestDTO setNewDeviceRequestForUser(UUID userID, String userPassword, String userSecret)
	{
		User userEntity = getEntityByID(userID);
		NewDeviceRequest newDeviceRequestEntity = NewDeviceRequest.createInstance(userPassword);
		newDeviceRequestEntity.encryptUserPassword(userSecret);
		boolean isUpdatingExistingRequest = userEntity.getNewDeviceRequest() != null;
		userEntity.setNewDeviceRequest(newDeviceRequestEntity);
		return NewDeviceRequestDTO.createInstance(User.getRepository().save(userEntity).getNewDeviceRequest(),
				isUpdatingExistingRequest);
	}

	@Transactional
	public NewDeviceRequestDTO getNewDeviceRequestForUser(UUID userID, String userSecret)
	{
		User userEntity = getEntityByID(userID);
		NewDeviceRequest newDeviceRequestEntity = userEntity.getNewDeviceRequest();
		if (newDeviceRequestEntity == null)
		{
			throw DeviceRequestException.noDeviceRequestPresent(userID);
		}
		if (isExpired(newDeviceRequestEntity))
		{
			throw DeviceRequestException.deviceRequestExpired(userID);
		}

		if (StringUtils.isBlank(userSecret))
		{
			return NewDeviceRequestDTO.createInstance(userEntity.getNewDeviceRequest());
		}
		else
		{
			newDeviceRequestEntity.decryptUserPassword(userSecret);
			return NewDeviceRequestDTO.createInstanceWithPassword(newDeviceRequestEntity);
		}
	}

	private boolean isExpired(NewDeviceRequest newDeviceRequestEntity)
	{
		Date creationTime = newDeviceRequestEntity.getCreationTime();
		return (creationTime.getTime() + getExpirationIntervalMillis() < System.currentTimeMillis());
	}

	private long getExpirationIntervalMillis()
	{
		return yonaProperties.getNewDeviceRequestExpirationDays() * 24 * 60 * 60 * 1000;
	}

	@Transactional
	public void clearNewDeviceRequestForUser(UUID userID)
	{
		User userEntity = getEntityByID(userID);
		NewDeviceRequest existingNewDeviceRequestEntity = userEntity.getNewDeviceRequest();
		if (existingNewDeviceRequestEntity != null)
		{
			userEntity.setNewDeviceRequest(null);
			User.getRepository().save(userEntity);
		}
	}

	/*
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
