/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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

	@Autowired
	UserServiceTempEncryptionContextExecutor tempEncryptionContextExecutor;

	// TODO: Do we need this? Currently unused.
	@Transactional
	public UserDTO getUser(String mobileNumber)
	{
		if (mobileNumber == null)
		{
			throw UserServiceException.missingMobileNumber();
		}

		User userEntity = findUserByMobileNumber(mobileNumber);
		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	@Transactional
	public boolean canAccessPrivateData(UUID id)
	{
		return getUserByIDAndGarantueed(id).canAccessPrivateData();
	}

	@Transactional
	public UserDTO getPublicUser(UUID id)
	{
		return UserDTO.createInstance(getUserByIDAndGarantueed(id));
	}

	@Transactional
	public UserDTO getPrivateUser(UUID id)
	{
		return UserDTO.createInstanceWithPrivateData(getUserByIDAndGarantueed(id));
	}
	
	@Transactional
	public UserDTO getPrivateValidatedUser(UUID id)
	{
		return UserDTO.createInstanceWithPrivateData(getValidatedUserbyId(id));
	}

	@Transactional
	public UserDTO addUser(UserDTO user)
	{
		validateUserFields(user);

		user.getPrivateData().getVpnProfile().setVpnPassword(generatePassword());

		User userEntity = user.createUserEntity();
		setUserUnconfirmedWithNewConfirmationCode(userEntity);
		userEntity = User.getRepository().save(userEntity);
		ldapUserService.createVPNAccount(userEntity.getVPNLoginID().toString(), userEntity.getVPNPassword());

		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(userEntity);
		if (!yonaProperties.getSms().isEnabled())
		{
			userDTO.setConfirmationCode(userEntity.getConfirmationCode());
		}
		sendMobileNumberConfirmationMessage(userEntity, SmsService.TemplateName_AddUserNumberConfirmation);
		return userDTO;
	}

	@Transactional
	public UserDTO confirmMobileNumber(UUID userID, String code)
	{
		User userEntity = getUserByIDAndGarantueed(userID);

		if (userEntity.getConfirmationCode() == null)
		{
			throw MobileNumberConfirmationException.confirmationCodeNotSet();
		}

		if (!userEntity.getConfirmationCode().equals(code))
		{
			throw MobileNumberConfirmationException.confirmationCodeMismatch();
		}

		if (userEntity.isMobileNumberConfirmed())
		{
			throw MobileNumberConfirmationException.mobileNumberAlreadyConfirmed();
		}

		userEntity.setConfirmationCode(null);
		userEntity.markMobileNumberConfirmed();
		User.getRepository().save(userEntity);

		return UserDTO.createInstance(userEntity);
	}

	@Transactional
	public User addUserCreatedOnBuddyRequest(UserDTO buddyUser, String tempPassword)
	{
		if (buddyUser == null)
		{
			throw UserServiceException.missingOrNullUser();
		}

		UUID savedUserID = CryptoSession.execute(Optional.of(tempPassword), null,
				() -> tempEncryptionContextExecutor.addUserCreatedOnBuddyRequest(buddyUser).getID());

		return getUserByIDAndGarantueed(savedUserID);
	}

	@Transactional
	public UserDTO updateUser(UUID id, UserDTO user)
	{
		User originalUserEntity = getUserByIDAndGarantueed(id);
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to update a user created on buddy request with its temp password
			throw UserServiceException.cannotUpdateBecauseCreatedOnBuddyRequest(id);
		}

		boolean isMobileNumberDifferent = !user.getMobileNumber().equals(originalUserEntity.getMobileNumber());
		User updatedUserEntity = user.updateUser(originalUserEntity);
		if (isMobileNumberDifferent)
		{
			setUserUnconfirmedWithNewConfirmationCode(updatedUserEntity);
		}
		User savedUserEntity = User.getRepository().save(updatedUserEntity);
		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(savedUserEntity);
		if (isMobileNumberDifferent)
		{
			if (!yonaProperties.getSms().isEnabled())
			{
				userDTO.setConfirmationCode(savedUserEntity.getConfirmationCode());
			}
			sendMobileNumberConfirmationMessage(updatedUserEntity, SmsService.TemplateName_ChangedUserNumberConfirmation);
		}
		return userDTO;
	}

	void setUserUnconfirmedWithNewConfirmationCode(User userEntity)
	{
		userEntity.markMobileNumberUnconfirmed();
		userEntity
				.setConfirmationCode(CryptoUtil.getRandomDigits(yonaProperties.getSms().getMobileNumberConfirmationCodeDigits()));
	}

	@Transactional
	public UserDTO updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDTO userResource)
	{
		User originalUserEntity = getUserByIDAndGarantueed(id);
		if (!originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to replace the password on an existing user
			throw UserServiceException.usernotCreatedOnBuddyRequest(id);
		}

		EncryptedUserData retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		User savedUserEntity = saveUserEncryptedDataWithNewPassword(retrievedEntitySet, userResource);
		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(savedUserEntity);
		if (!yonaProperties.getSms().isEnabled())
		{
			userDTO.setConfirmationCode(savedUserEntity.getConfirmationCode());
		}
		sendMobileNumberConfirmationMessage(savedUserEntity, SmsService.TemplateName_AddUserNumberConfirmation);
		return userDTO;
	}

	@Transactional
	public void deleteUser(UUID id, Optional<String> message)
	{
		User userEntity = getUserByIDAndGarantueed(id);

		userEntity.getBuddies().forEach(buddyEntity -> buddyService.removeBuddyInfoForBuddy(userEntity, buddyEntity, message,
				DropBuddyReason.USER_ACCOUNT_DELETED));

		UserAnonymized userAnonymized = userEntity.getAnonymized();
		UUID vpnLoginID = userAnonymized.getVPNLoginID();
		UserAnonymized.getRepository().delete(userAnonymized);
		MessageSource namedMessageSource = userEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = userEntity.getAnonymousMessageSource();
		MessageSource.getRepository().delete(anonymousMessageSource);
		MessageSource.getRepository().delete(namedMessageSource);
		User.getRepository().delete(userEntity);

		ldapUserService.deleteVPNAccount(vpnLoginID.toString());
	}

	public void addBuddy(UserDTO user, BuddyDTO buddy)
	{
		if (user == null || user.getID() == null)
		{
			throw InvalidDataException.emptyUserId();
		}

		if (buddy == null || buddy.getID() == null)
		{
			throw InvalidDataException.emptyBuddyId();
		}

		User userEntity = getUserByIDAndGarantueed(user.getID());
		userEntity.assertMobileNumberConfirmed();

		Buddy buddyEntity = Buddy.getRepository().findOne(buddy.getID());
		userEntity.addBuddy(buddyEntity);

		User.getRepository().save(userEntity);
	}

	public String generatePassword()
	{
		return CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength());
	}

	@Transactional
	public NewDeviceRequestDTO setNewDeviceRequestForUser(UUID userID, String userPassword, String userSecret)
	{
		User userEntity = getValidatedUserbyId(userID);

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
		User userEntity = getValidatedUserbyId(userID);
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

	@Transactional
	public void clearNewDeviceRequestForUser(UUID userID)
	{
		User userEntity = getValidatedUserbyId(userID);

		NewDeviceRequest existingNewDeviceRequestEntity = userEntity.getNewDeviceRequest();
		if (existingNewDeviceRequestEntity != null)
		{
			userEntity.setNewDeviceRequest(null);
			User.getRepository().save(userEntity);
		}
	}

	static User findUserByMobileNumber(String mobileNumber)
	{
		User userEntity = User.getRepository().findByMobileNumber(mobileNumber);
		if (userEntity == null)
		{
			throw UserServiceException.notFoundByMobileNumber(mobileNumber);
		}
		return userEntity;
	}

	public static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(() -> UserServiceException.missingPasswordHeader());
	}

	/**
	 * This method returns a user entity. The passed on Id is checked whether or not it is set. it also checks that the return
	 * value is always the user entity. If not an exception is thrown.
	 * 
	 * @param id the ID of the user
	 * @return The user entity
	 */
	public User getUserByIDAndGarantueed(UUID id)
	{
		if (id == null)
		{
			throw InvalidDataException.emptyUserId();
		}

		User entity = User.getRepository().findOne(id);

		if (entity == null)
		{
			throw UserServiceException.notFoundByID(id);
		}

		return entity;
	}

	/**
	 * This method returns a validated user entity. A validated user means a user with a confirmed mobile number.
	 * 
	 * @param id The id of the user.
	 * @return The validated user entity. An exception is thrown is something is missing.
	 */
	public User getValidatedUserbyId(UUID id)
	{
		User retVal = getUserByIDAndGarantueed(id);
		retVal.assertMobileNumberConfirmed();

		return retVal;
	}

	private void sendMobileNumberConfirmationMessage(User userEntity, String messageTemplateName)
	{
		String confirmationCode = userEntity.getConfirmationCode();
		if (confirmationCode == null)
		{
			throw MobileNumberConfirmationException.confirmationCodeNotSet();
		}
		Map<String, Object> templateParams = new HashMap<String, Object>();
		templateParams.put("confirmationCode", confirmationCode);
		smsService.send(userEntity.getMobileNumber(), messageTemplateName, templateParams);
	}

	private EncryptedUserData retrieveUserEncryptedData(User originalUserEntity, String password)
	{
		return CryptoSession.execute(Optional.of(password), () -> canAccessPrivateData(originalUserEntity.getID()),
				() -> tempEncryptionContextExecutor.retrieveUserEncryptedData(originalUserEntity));
	}

	private User saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDTO userResource)
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
		return User.getRepository().save(retrievedEntitySet.userEntity);
	}

	private boolean isExpired(NewDeviceRequest newDeviceRequestEntity)
	{
		Date creationTime = newDeviceRequestEntity.getCreationTime();
		return (creationTime.getTime() + getExpirationIntervalMillis() < System.currentTimeMillis());
	}

	private long getExpirationIntervalMillis()
	{
		return yonaProperties.getSecurity().getNewDeviceRequestExpirationDays() * 24 * 60 * 60 * 1000;
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
