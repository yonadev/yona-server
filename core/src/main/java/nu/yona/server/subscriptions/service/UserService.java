/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
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
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		ConfirmationCode confirmationCode = createConfirmationCode();
		existingUserEntity.setOverwriteUserConfirmationCode(confirmationCode);
		User.getRepository().save(existingUserEntity);
		sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, SmsService.TemplateName_OverwriteUserNumberConfirmation);
	}

	@Transactional
	public void clearOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		existingUserEntity.setOverwriteUserConfirmationCode(null);
		User.getRepository().save(existingUserEntity);
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
		Optional<ConfirmationCode> confirmationCode = Optional.empty();
		if (overwriteUserConfirmationCode.isPresent())
		{
			// no need to confirm again
			userEntity.markMobileNumberConfirmed();
		}
		else
		{
			confirmationCode = Optional.of(createConfirmationCode());
			userEntity.setMobileNumberConfirmationCode(confirmationCode.get());
		}
		userEntity = User.getRepository().save(userEntity);
		ldapUserService.createVPNAccount(userEntity.getUserAnonymizedID().toString(), userEntity.getVPNPassword());

		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(userEntity);
		if (confirmationCode.isPresent())
		{
			sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
					SmsService.TemplateName_AddUserNumberConfirmation);
		}

		logger.info("Added new user with mobile number '{}' and ID '{}'", userDTO.getMobileNumber(), userDTO.getID());
		return userDTO;
	}

	private void addMandatoryGoals(User userEntity)
	{
		activityCategoryService.getAllActivityCategories().stream().filter(c -> c.isMandatoryNoGo())
				.forEach(c -> addGoal(userEntity, c));
	}

	private void addGoal(User userEntity, ActivityCategoryDTO category)
	{
		userEntity.getAnonymized()
				.addGoal(BudgetGoal.createNoGoInstance(ActivityCategory.getRepository().findOne(category.getID())));
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

	private void deleteExistingUserToOverwriteIt(String mobileNumber, String userProvidedConfirmationCode)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		ConfirmationCode confirmationCode = existingUserEntity.getOverwriteUserConfirmationCode();

		verifyConfirmationCode(existingUserEntity, confirmationCode, userProvidedConfirmationCode,
				() -> UserOverwriteConfirmationException.confirmationCodeNotSet(existingUserEntity.getMobileNumber()),
				(r) -> UserOverwriteConfirmationException.confirmationCodeMismatch(existingUserEntity.getMobileNumber(),
						userProvidedConfirmationCode, r),
				() -> UserOverwriteConfirmationException.tooManyAttempts(existingUserEntity.getMobileNumber()));

		// notice we can't delete the associated anonymized data
		// because the anonymized data cannot be retrieved
		// (the relation is encrypted, the password is not available)
		User.getRepository().delete(existingUserEntity);
	}

	@Transactional
	public UserDTO confirmMobileNumber(UUID userID, String userProvidedConfirmationCode)
	{
		User userEntity = getUserByID(userID);
		ConfirmationCode confirmationCode = userEntity.getMobileNumberConfirmationCode();

		verifyConfirmationCode(userEntity, confirmationCode, userProvidedConfirmationCode,
				() -> MobileNumberConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber()),
				(r) -> MobileNumberConfirmationException.confirmationCodeMismatch(userEntity.getMobileNumber(),
						userProvidedConfirmationCode, r),
				() -> MobileNumberConfirmationException.tooManyAttempts(userEntity.getMobileNumber()));

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
	public Object resendMobileNumberConfirmationCode(UUID userID)
	{
		User userEntity = getUserByID(userID);
		ConfirmationCode confirmationCode = createConfirmationCode();
		userEntity.setMobileNumberConfirmationCode(confirmationCode);
		User.getRepository().save(userEntity);
		sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
				SmsService.TemplateName_AddUserNumberConfirmation);
		return null;
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
				CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength()), Collections.emptySet());
		addMandatoryGoals(newUser);
		newUser.setIsCreatedOnBuddyRequest();
		newUser.setMobileNumberConfirmationCode(createConfirmationCode());
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
		Optional<ConfirmationCode> confirmationCode = Optional.empty();
		if (isMobileNumberDifferent)
		{
			confirmationCode = Optional.of(createConfirmationCode());
			updatedUserEntity.setMobileNumberConfirmationCode(confirmationCode.get());
		}
		User savedUserEntity = User.getRepository().save(updatedUserEntity);
		UserDTO userDTO = UserDTO.createInstanceWithPrivateData(savedUserEntity);
		if (confirmationCode.isPresent())
		{
			sendConfirmationCodeTextMessage(updatedUserEntity.getMobileNumber(), confirmationCode.get(),
					SmsService.TemplateName_ChangedUserNumberConfirmation);
		}
		logger.info("Updated user with mobile number '{}' and ID '{}'", userDTO.getMobileNumber(), userDTO.getID());
		return userDTO;
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
		sendConfirmationCodeTextMessage(savedUserEntity.getMobileNumber(), savedUserEntity.getMobileNumberConfirmationCode(),
				SmsService.TemplateName_AddUserNumberConfirmation);
		return UserDTO.createInstanceWithPrivateData(savedUserEntity);
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

		WeekActivity.getRepository().deleteAllForUser(userEntity.getUserAnonymizedID());

		UUID vpnLoginID = userEntity.getVPNLoginID();
		UUID userAnonymizedID = userEntity.getUserAnonymizedID();
		userAnonymizedService.deleteUserAnonymized(userAnonymizedID);
		MessageSource namedMessageSource = userEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = userEntity.getAnonymousMessageSource();
		MessageSource.getRepository().delete(anonymousMessageSource);
		MessageSource.getRepository().delete(namedMessageSource);
		User.getRepository().delete(userEntity);

		ldapUserService.deleteVPNAccount(vpnLoginID.toString());
		logger.info("Deleted user with mobile number '{}' and ID '{}'", userEntity.getMobileNumber(), userEntity.getID());
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

	public UserDTO getUserByMobileNumber(String mobileNumber)
	{
		return UserDTO.createInstance(findUserByMobileNumber(mobileNumber));
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

	private ConfirmationCode createConfirmationCode()
	{
		String confirmationCode = (yonaProperties.getSms().isEnabled())
				? CryptoUtil.getRandomDigits(yonaProperties.getSecurity().getConfirmationCodeDigits()) : "1234";
		return ConfirmationCode.createInstance(confirmationCode);
	}

	private void sendConfirmationCodeTextMessage(String mobileNumber, ConfirmationCode confirmationCode, String templateName)
	{
		Map<String, Object> templateParams = new HashMap<String, Object>();
		templateParams.put("confirmationCode", confirmationCode);
		smsService.send(mobileNumber, templateName, templateParams);
	}

	private void verifyConfirmationCode(User userEntity, ConfirmationCode confirmationCode, String userProvidedConfirmationCode,
			Supplier<YonaException> noConfirmationCodeExceptionSupplier,
			Function<Integer, YonaException> invalidConfirmationCodeExceptionSupplier,
			Supplier<YonaException> tooManyAttemptsExceptionSupplier)
	{
		if (confirmationCode == null)
		{
			throw noConfirmationCodeExceptionSupplier.get();
		}

		int remainingAttempts = yonaProperties.getSecurity().getConfirmationCodeMaxAttempts() - confirmationCode.getAttempts();
		if (remainingAttempts <= 0)
		{
			throw tooManyAttemptsExceptionSupplier.get();
		}

		if (!confirmationCode.getConfirmationCode().equals(userProvidedConfirmationCode))
		{
			registerFailedAttempt(userEntity, confirmationCode);
			throw invalidConfirmationCodeExceptionSupplier.apply(remainingAttempts - 1);
		}
	}

	private void registerFailedAttempt(User userEntity, ConfirmationCode confirmationCode)
	{
		transactionHelper.executeInNewTransaction(() -> {
			confirmationCode.incrementAttempts();
			User.getRepository().save(userEntity);
			return null;
		});
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

		if (StringUtils.isBlank(userResource.getPrivateData().getNickname()))
		{
			throw InvalidDataException.blankNickname();
		}

		if (StringUtils.isBlank(userResource.getMobileNumber()))
		{
			throw InvalidDataException.blankMobileNumber();
		}

		validateMobileNumber(userResource.getMobileNumber());
	}

	public void validateMobileNumber(String mobileNumber)
	{
		if (!REGEX_PHONE.matcher(mobileNumber).matches())
		{
			throw InvalidDataException.invalidMobileNumber(mobileNumber);
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
