/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
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

	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

	@Autowired
	private LDAPUserService ldapUserService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private SmsService smsService;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private TransactionHelper transactionHelper;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Autowired
	private ActivityCategoryService activityCategoryService;

	@Transactional
	public boolean canAccessPrivateData(UUID id)
	{
		return getUserByID(id).canAccessPrivateData();
	}

	@Transactional
	public UserDTO getPublicUser(UUID id)
	{
		return UserDTO.createInstance(getUserByID(id));
	}

	@Transactional
	public UserDTO getPrivateUser(UUID id)
	{
		return UserDTO.createInstanceWithPrivateData(getUserByID(id));
	}

	@Transactional
	public UserDTO getPrivateValidatedUser(UUID id)
	{
		return UserDTO.createInstanceWithPrivateData(getValidatedUserbyID(id));
	}

	@Transactional
	public OverwriteUserDTO setOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		existingUserEntity.setOverwriteUserConfirmationCode(
				CryptoUtil.getRandomDigits(yonaProperties.getSms().getMobileNumberConfirmationCodeDigits()));
		User.getRepository().save(existingUserEntity);
		OverwriteUserDTO overwriteUserDTO = OverwriteUserDTO.createInstance();
		if (!yonaProperties.getSms().isEnabled())
		{
			overwriteUserDTO.setConfirmationCode(existingUserEntity.getOverwriteUserConfirmationCode());
		}
		sendOverwriteUserConfirmationMessage(mobileNumber, existingUserEntity);
		return overwriteUserDTO;
	}

	@Transactional
	public void clearOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		existingUserEntity.setOverwriteUserConfirmationCode(null);
		User.getRepository().save(existingUserEntity);
	}

	private void sendOverwriteUserConfirmationMessage(String mobileNumber, User existingUserEntity)
	{
		Map<String, Object> templateParams = new HashMap<String, Object>();
		templateParams.put("confirmationCode", existingUserEntity.getOverwriteUserConfirmationCode());
		smsService.send(mobileNumber, SmsService.TemplateName_OverwriteUserNumberConfirmation, templateParams);
	}

	@Transactional
	public UserDTO addUser(UserDTO user, Optional<String> overwriteUserConfirmationCode)
	{
		validateUserFields(user);

		// use a separate transaction because in the top transaction we insert a user with the same unique key
		transactionHelper.executeInNewTransaction(() -> {
			handleExistingUserForMobileNumber(user.getMobileNumber(), overwriteUserConfirmationCode);
			return null;
		});

		user.getPrivateData().getVpnProfile().setVpnPassword(generatePassword());

		User userEntity = user.createUserEntity();
		addMandatoryGoals(userEntity);
		if (overwriteUserConfirmationCode.isPresent())
		{
			// no need to confirm again
			userEntity.markMobileNumberConfirmed();
		}
		else
		{
			setUserUnconfirmedWithNewConfirmationCode(userEntity);
		}
		userEntity = User.getRepository().save(userEntity);
		ldapUserService.createVPNAccount(userEntity.getUserAnonymizedID().toString(), userEntity.getVPNPassword());

		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(userEntity);
		if (!userDTO.isMobileNumberConfirmed())
		{
			if (!yonaProperties.getSms().isEnabled())
			{
				userDTO.setMobileNumberConfirmationCode(userEntity.getMobileNumberConfirmationCode());
			}
			sendMobileNumberConfirmationMessage(userEntity, SmsService.TemplateName_AddUserNumberConfirmation);
		}

		logger.info("Added new user with mobile number '{}' and ID '{}'", userDTO.getMobileNumber(), userDTO.getID());
		return userDTO;
	}

	private void addMandatoryGoals(User userEntity)
	{
		for (ActivityCategoryDTO activityCategory : activityCategoryService.getAllActivityCategories())
		{
			if (activityCategory.isMandatoryNoGo())
			{
				userEntity.getAnonymized().addGoal(
						BudgetGoal.createNoGoInstance(ActivityCategory.getRepository().findOne(activityCategory.getID())));
			}
		}
	}

	private void handleExistingUserForMobileNumber(String mobileNumber, Optional<String> overwriteUserConfirmationCode)
	{
		if (overwriteUserConfirmationCode.isPresent())
		{
			deleteExistingUserToOverwriteIt(mobileNumber, overwriteUserConfirmationCode.get());
		}
		else
		{
			verifyUserDoesNotExist(mobileNumber);
		}
	}

	private void verifyUserDoesNotExist(String mobileNumber)
	{
		User existingUser = User.getRepository().findByMobileNumber(mobileNumber);
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

	private void deleteExistingUserToOverwriteIt(String mobileNumber, String overwriteUserConfirmationCode)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		String expectedOverwriteUserConfirmationCode = existingUserEntity.getOverwriteUserConfirmationCode();
		if (expectedOverwriteUserConfirmationCode == null)
		{
			throw UserOverwriteConfirmationException.confirmationCodeNotSet(mobileNumber);
		}
		if (!expectedOverwriteUserConfirmationCode.equals(overwriteUserConfirmationCode))
		{
			throw UserOverwriteConfirmationException.confirmationCodeMismatch(mobileNumber, overwriteUserConfirmationCode);
		}
		User.getRepository().delete(existingUserEntity);
	}

	@Transactional
	public UserDTO confirmMobileNumber(UUID userID, String code)
	{
		User userEntity = getUserByID(userID);

		if (userEntity.getMobileNumberConfirmationCode() == null)
		{
			throw MobileNumberConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber());
		}

		if (!userEntity.getMobileNumberConfirmationCode().equals(code))
		{
			throw MobileNumberConfirmationException.confirmationCodeMismatch(userEntity.getMobileNumber(), code);
		}

		if (userEntity.isMobileNumberConfirmed())
		{
			throw MobileNumberConfirmationException.mobileNumberAlreadyConfirmed(userEntity.getMobileNumber());
		}

		userEntity.setMobileNumberConfirmationCode(null);
		userEntity.markMobileNumberConfirmed();
		User.getRepository().save(userEntity);

		return UserDTO.createInstanceWithPrivateData(userEntity);
	}

	@Transactional
	public User addUserCreatedOnBuddyRequest(UserDTO buddyUser, String tempPassword)
	{
		if (buddyUser == null)
		{
			throw UserServiceException.missingOrNullUser();
		}

		// use a separate transaction to commit within the crypto session
		UUID savedUserID = CryptoSession.execute(Optional.of(tempPassword), null, () -> transactionHelper
				.executeInNewTransaction(() -> addUserCreatedOnBuddyRequestInSubTransaction(buddyUser).getID()));

		return getUserByID(savedUserID);
	}

	private User addUserCreatedOnBuddyRequestInSubTransaction(UserDTO buddyUserResource)
	{
		User newUser = User.createInstance(buddyUserResource.getFirstName(), buddyUserResource.getLastName(),
				buddyUserResource.getPrivateData().getNickname(), buddyUserResource.getMobileNumber(),
				CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength()), Collections.emptySet(),
				Collections.emptySet());
		addMandatoryGoals(newUser);
		newUser.setIsCreatedOnBuddyRequest();
		setUserUnconfirmedWithNewConfirmationCode(newUser);
		User savedUser = User.getRepository().save(newUser);
		ldapUserService.createVPNAccount(savedUser.getUserAnonymizedID().toString(), savedUser.getVPNPassword());
		return savedUser;
	}

	@Transactional
	public UserDTO updateUser(UUID id, UserDTO user)
	{
		User originalUserEntity = getUserByID(id);
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
				userDTO.setMobileNumberConfirmationCode(savedUserEntity.getMobileNumberConfirmationCode());
			}
			sendMobileNumberConfirmationMessage(updatedUserEntity, SmsService.TemplateName_ChangedUserNumberConfirmation);
		}
		logger.info("Updated user with mobile number '{}' and ID '{}'", userDTO.getMobileNumber(), userDTO.getID());
		return userDTO;
	}

	void setUserUnconfirmedWithNewConfirmationCode(User userEntity)
	{
		userEntity.markMobileNumberUnconfirmed();
		userEntity.setMobileNumberConfirmationCode(
				CryptoUtil.getRandomDigits(yonaProperties.getSms().getMobileNumberConfirmationCodeDigits()));
	}

	@Transactional
	public UserDTO updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDTO userResource)
	{
		User originalUserEntity = getUserByID(id);
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
			userDTO.setMobileNumberConfirmationCode(savedUserEntity.getMobileNumberConfirmationCode());
		}
		sendMobileNumberConfirmationMessage(savedUserEntity, SmsService.TemplateName_AddUserNumberConfirmation);
		return userDTO;
	}

	private EncryptedUserData retrieveUserEncryptedData(User originalUserEntity, String password)
	{
		// use a separate transaction to read within the crypto session
		return CryptoSession.execute(Optional.of(password), () -> canAccessPrivateData(originalUserEntity.getID()),
				() -> transactionHelper
						.executeInNewTransaction(() -> retrieveUserEncryptedDataInSubTransaction(originalUserEntity)));
	}

	private EncryptedUserData retrieveUserEncryptedDataInSubTransaction(User originalUserEntity)
	{
		MessageSource namedMessageSource = originalUserEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = originalUserEntity.getAnonymousMessageSource();
		EncryptedUserData userEncryptedData = new EncryptedUserData(originalUserEntity, namedMessageSource,
				anonymousMessageSource);
		userEncryptedData.loadLazyEncryptedData();
		return userEncryptedData;
	}

	@Transactional
	public void deleteUser(UUID id, Optional<String> message)
	{
		User userEntity = getUserByID(id);

		userEntity.getBuddies().forEach(buddyEntity -> buddyService.removeBuddyInfoForBuddy(userEntity, buddyEntity, message,
				DropBuddyReason.USER_ACCOUNT_DELETED));

		UUID vpnLoginID = userEntity.getVPNLoginID();
		UUID userAnonymizedID = userEntity.getUserAnonymizedID();
		userAnonymizedService.deleteUserAnonymized(userAnonymizedID);
		MessageSource namedMessageSource = userEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = userEntity.getAnonymousMessageSource();
		MessageSource.getRepository().delete(anonymousMessageSource);
		MessageSource.getRepository().delete(namedMessageSource);
		User.getRepository().delete(userEntity);

		ldapUserService.deleteVPNAccount(vpnLoginID.toString());
		logger.info("Updated user with mobile number '{}' and ID '{}'", userEntity.getMobileNumber(), userEntity.getID());
	}

	@Transactional
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

		User userEntity = getUserByID(user.getID());
		userEntity.assertMobileNumberConfirmed();

		Buddy buddyEntity = Buddy.getRepository().findOne(buddy.getID());
		userEntity.addBuddy(buddyEntity);
		User.getRepository().save(userEntity);

		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		userAnonymizedEntity.addBuddyAnonymized(buddyEntity.getBuddyAnonymized());
		userAnonymizedService.updateUserAnonymized(userEntity.getUserAnonymizedID(), userAnonymizedEntity);
	}

	public String generatePassword()
	{
		return CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength());
	}

	@Transactional
	public NewDeviceRequestDTO setNewDeviceRequestForUser(UUID userID, String userPassword, String userSecret)
	{
		User userEntity = getValidatedUserbyID(userID);

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
		User userEntity = getValidatedUserbyID(userID);
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
		User userEntity = getValidatedUserbyID(userID);

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
	 * @return The user entity (never null)
	 */
	public User getUserByID(UUID id)
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
	public User getValidatedUserbyID(UUID id)
	{
		User retVal = getUserByID(id);
		retVal.assertMobileNumberConfirmed();

		return retVal;
	}

	private void sendMobileNumberConfirmationMessage(User userEntity, String messageTemplateName)
	{
		String confirmationCode = userEntity.getMobileNumberConfirmationCode();
		if (confirmationCode == null)
		{
			throw MobileNumberConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber());
		}
		Map<String, Object> templateParams = new HashMap<String, Object>();
		templateParams.put("confirmationCode", confirmationCode);
		smsService.send(userEntity.getMobileNumber(), messageTemplateName, templateParams);
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
