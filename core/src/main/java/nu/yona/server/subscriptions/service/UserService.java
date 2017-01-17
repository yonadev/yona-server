/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;
import nu.yona.server.util.TimeUtil;
import nu.yona.server.util.TransactionHelper;

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

	@Autowired
	private MessageService messageService;

	@Autowired
	private WhiteListedNumberService whiteListedNumberService;

	@PostConstruct
	private void onStart()
	{
		int maxUsers = yonaProperties.getMaxUsers();
		if (maxUsers <= 0)
		{
			return;
		}

		logger.info("Maximum number of users: {}", maxUsers);
	}

	@Transactional
	public boolean canAccessPrivateData(UUID id)
	{
		return getUserEntityById(id).canAccessPrivateData();
	}

	@Transactional
	public UserDto getPublicUser(UUID id)
	{
		return UserDto.createInstance(getUserEntityById(id));
	}

	@Transactional
	public UserDto getPrivateUser(UUID id)
	{
		User user = getUserEntityById(id);
		handleBuddyUsersRemovedWhileOffline(user);
		return createUserDtoWithPrivateData(user);
	}

	@Transactional
	public UserDto getPrivateValidatedUser(UUID id)
	{
		User validatedUser = getValidatedUserbyId(id);
		handleBuddyUsersRemovedWhileOffline(validatedUser);
		return createUserDtoWithPrivateData(validatedUser);
	}

	@Transactional
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		ConfirmationCode confirmationCode = createConfirmationCode();
		existingUserEntity.setOverwriteUserConfirmationCode(confirmationCode);
		User.getRepository().save(existingUserEntity);
		sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, SmsService.TemplateName_OverwriteUserConfirmation);
		logger.info("User with mobile number '{}' and ID '{}' requested an account overwrite confirmation code",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional
	public void clearOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		existingUserEntity.setOverwriteUserConfirmationCode(null);
		User.getRepository().save(existingUserEntity);
		logger.info("User with mobile number '{}' and ID '{}' cleared the account overwrite confirmation code",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional
	public UserDto addUser(UserDto user, Optional<String> overwriteUserConfirmationCode)
	{
		validateUserFields(user);

		// use a separate transaction because in the top transaction we insert a user with the same unique key
		transactionHelper.executeInNewTransaction(
				() -> handleExistingUserForMobileNumber(user.getMobileNumber(), overwriteUserConfirmationCode));

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
		ldapUserService.createVpnAccount(userEntity.getUserAnonymizedId().toString(), userEntity.getVpnPassword());

		UserDto userDto = createUserDtoWithPrivateData(userEntity);
		if (confirmationCode.isPresent())
		{
			sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
					SmsService.TemplateName_AddUserNumberConfirmation);
		}

		logger.info("Added new user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
		return userDto;
	}

	UserDto createUserDtoWithPrivateData(User user)
	{
		return UserDto.createInstanceWithPrivateData(user,
				(buddyIds) -> buddyIds.stream().map((bid) -> buddyService.getBuddy(bid)).collect(Collectors.toSet()));
	}

	private void addMandatoryGoals(User userEntity)
	{
		activityCategoryService.getAllActivityCategories().stream().filter(c -> c.isMandatoryNoGo())
				.forEach(c -> addNoGoGoal(userEntity, c));
	}

	private void addNoGoGoal(User userEntity, ActivityCategoryDto category)
	{
		userEntity.getAnonymized().addGoal(
				BudgetGoal.createNoGoInstance(TimeUtil.utcNow(), ActivityCategory.getRepository().findOne(category.getId())));
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
			verifyUserIsAllowed(mobileNumber);
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

	private void verifyUserIsAllowed(String mobileNumber)
	{
		int maxUsers = yonaProperties.getMaxUsers();
		if (maxUsers <= 0)
		{
			whiteListedNumberService.verifyMobileNumberIsAllowed(mobileNumber);
			return;
		}

		long currentNumberOfUsers = User.getRepository().count();
		if (currentNumberOfUsers < maxUsers)
		{
			if (currentNumberOfUsers >= maxUsers - maxUsers / 10)
			{
				logger.warn("Nearing the maximum number of users. Current number: {}, maximum: {}", currentNumberOfUsers,
						maxUsers);
			}
			return;
		}

		throw UserServiceException.maximumNumberOfUsersReached();
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
		logger.info("User with mobile number '{}' and ID '{}' removed, to overwrite the account",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional
	public UserDto confirmMobileNumber(UUID userId, String userProvidedConfirmationCode)
	{
		User userEntity = getUserEntityById(userId);
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

		logger.info("User with mobile number '{}' and ID '{}' successfully confirmed their mobile number",
				userEntity.getMobileNumber(), userEntity.getId());
		return createUserDtoWithPrivateData(userEntity);
	}

	@Transactional
	public Object resendMobileNumberConfirmationCode(UUID userId)
	{
		User userEntity = getUserEntityById(userId);
		logger.info("User with mobile number '{}' and ID '{}' requests to resend the mobile number confirmation code",
				userEntity.getMobileNumber(), userEntity.getId());
		ConfirmationCode confirmationCode = createConfirmationCode();
		userEntity.setMobileNumberConfirmationCode(confirmationCode);
		User.getRepository().save(userEntity);
		sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
				SmsService.TemplateName_AddUserNumberConfirmation);
		return null;
	}

	@Transactional
	User addUserCreatedOnBuddyRequest(UserDto buddyUserResource)
	{
		verifyUserIsAllowed(buddyUserResource.getMobileNumber());
		User newUser = User.createInstance(buddyUserResource.getFirstName(), buddyUserResource.getLastName(),
				buddyUserResource.getPrivateData().getNickname(), buddyUserResource.getMobileNumber(),
				CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength()), Collections.emptySet());
		addMandatoryGoals(newUser);
		newUser.setIsCreatedOnBuddyRequest();
		newUser.setMobileNumberConfirmationCode(createConfirmationCode());
		User savedUser = User.getRepository().save(newUser);
		ldapUserService.createVpnAccount(savedUser.getUserAnonymizedId().toString(), savedUser.getVpnPassword());
		logger.info("User with mobile number '{}' and ID '{}' created on buddy request", savedUser.getMobileNumber(),
				savedUser.getId());
		return savedUser;
	}

	@Transactional
	public UserDto updateUser(UUID id, UserDto user)
	{
		User originalUserEntity = getUserEntityById(id);
		UserDto originalUser = createUserDtoWithPrivateData(originalUserEntity);
		validateUpdateRequest(user, originalUser, originalUserEntity);

		User updatedUserEntity = user.updateUser(originalUserEntity);
		Optional<ConfirmationCode> confirmationCode = Optional.empty();
		if (isMobileNumberDifferent(user, originalUser))
		{
			confirmationCode = Optional.of(createConfirmationCode());
			updatedUserEntity.setMobileNumberConfirmationCode(confirmationCode.get());
		}
		User savedUserEntity = User.getRepository().save(updatedUserEntity);
		UserDto userDto = createUserDtoWithPrivateData(savedUserEntity);
		if (confirmationCode.isPresent())
		{
			sendConfirmationCodeTextMessage(updatedUserEntity.getMobileNumber(), confirmationCode.get(),
					SmsService.TemplateName_ChangedUserNumberConfirmation);
		}
		logger.info("Updated user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
		buddyService.broadcastUserInfoChangeToBuddies(savedUserEntity, originalUser);
		return userDto;
	}

	private void validateUpdateRequest(UserDto user, UserDto originalUser, User originalUserEntity)
	{
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to update a user created on buddy request with its temp password
			throw UserServiceException.cannotUpdateBecauseCreatedOnBuddyRequest(user.getId());
		}
		if (isMobileNumberDifferent(user, originalUser))
		{
			verifyUserDoesNotExist(user.getMobileNumber());
		}
	}

	private boolean isMobileNumberDifferent(UserDto user, UserDto originalUser)
	{
		return !user.getMobileNumber().equals(originalUser.getMobileNumber());
	}

	@Transactional
	public UserDto updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDto userResource)
	{
		User originalUserEntity = getUserEntityById(id);
		if (!originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to replace the password on an existing user
			throw UserServiceException.usernotCreatedOnBuddyRequest(id);
		}

		EncryptedUserData retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		User savedUserEntity = saveUserEncryptedDataWithNewPassword(retrievedEntitySet, userResource);
		sendConfirmationCodeTextMessage(savedUserEntity.getMobileNumber(), savedUserEntity.getMobileNumberConfirmationCode(),
				SmsService.TemplateName_AddUserNumberConfirmation);
		UserDto userDto = createUserDtoWithPrivateData(savedUserEntity);
		logger.info("Updated user (created on buddy request) with mobile number '{}' and ID '{}'", userDto.getMobileNumber(),
				userDto.getId());
		return userDto;
	}

	private EncryptedUserData retrieveUserEncryptedData(User originalUserEntity, String password)
	{
		// use a separate crypto session to read the data based on the temporary password
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(password),
				() -> canAccessPrivateData(originalUserEntity.getId())))
		{
			return retrieveUserEncryptedDataInNewCryptoSession(originalUserEntity);
		}
	}

	private EncryptedUserData retrieveUserEncryptedDataInNewCryptoSession(User originalUserEntity)
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
		User userEntity = getUserEntityById(id);
		buddyService.processPossiblePendingBuddyResponseMessages(userEntity);

		handleBuddyUsersRemovedWhileOffline(userEntity);

		userEntity.getBuddies().forEach(buddyEntity -> buddyService.removeBuddyInfoForBuddy(userEntity, buddyEntity, message,
				DropBuddyReason.USER_ACCOUNT_DELETED));

		UUID vpnLoginId = userEntity.getVpnLoginId();
		UUID userAnonymizedId = userEntity.getUserAnonymizedId();
		MessageSource namedMessageSource = userEntity.getNamedMessageSource();
		MessageSource anonymousMessageSource = userEntity.getAnonymousMessageSource();
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId);
		userAnonymizedEntity.clearAnonymousDestination();
		userAnonymizedEntity = userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		userEntity.clearNamedMessageDestination();
		User updatedUserEntity = User.getRepository().saveAndFlush(userEntity);

		MessageSource.getRepository().delete(anonymousMessageSource);
		MessageSource.getRepository().delete(namedMessageSource);
		MessageSource.getRepository().flush();

		Set<Goal> allGoalsIncludingHistoryItems = getAllGoalsIncludingHistoryItems(updatedUserEntity);
		deleteAllWeekActivityCommentMessages(allGoalsIncludingHistoryItems);
		deleteAllDayActivitiesWithTheirCommentMessages(allGoalsIncludingHistoryItems);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);

		allGoalsIncludingHistoryItems.forEach(g -> g.removeAllWeekActivities());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);

		userAnonymizedService.deleteUserAnonymized(userAnonymizedId);
		User.getRepository().delete(updatedUserEntity);

		ldapUserService.deleteVpnAccount(vpnLoginId.toString());
		logger.info("Deleted user with mobile number '{}' and ID '{}'", updatedUserEntity.getMobileNumber(),
				updatedUserEntity.getId());
	}

	private void deleteAllDayActivitiesWithTheirCommentMessages(Set<Goal> allGoalsIncludingHistoryItems)
	{
		allGoalsIncludingHistoryItems.forEach(g -> g.getWeekActivities().forEach(wa -> {
			// Other users might have commented on the activities being deleted. Delete these messages.
			messageService.deleteMessagesForIntervalActivities(wa.getDayActivities().stream().collect(Collectors.toList()));
			wa.removeAllDayActivities();
		}));
	}

	private void deleteAllWeekActivityCommentMessages(Set<Goal> allGoalsIncludingHistoryItems)
	{
		// Other users might have commented on the activities being deleted. Delete these messages.
		List<IntervalActivity> allWeekActivities = allGoalsIncludingHistoryItems.stream()
				.flatMap(g -> g.getWeekActivities().stream()).collect(Collectors.toList());
		messageService.deleteMessagesForIntervalActivities(allWeekActivities);
	}

	private Set<Goal> getAllGoalsIncludingHistoryItems(User userEntity)
	{
		Set<Goal> allGoals = new HashSet<>(userEntity.getGoals());
		Set<Goal> historyItems = new HashSet<>();
		for (Goal goal : allGoals)
		{
			Optional<Goal> historyItem = goal.getPreviousVersionOfThisGoal();
			while (historyItem.isPresent())
			{
				historyItems.add(historyItem.get());
				historyItem = historyItem.get().getPreviousVersionOfThisGoal();
			}
		}
		allGoals.addAll(historyItems);
		return allGoals;
	}

	@Transactional
	public void addBuddy(UserDto user, BuddyDto buddy)
	{
		if (user == null || user.getId() == null)
		{
			throw InvalidDataException.emptyUserId();
		}

		if (buddy == null || buddy.getId() == null)
		{
			throw InvalidDataException.emptyBuddyId();
		}

		User userEntity = getUserEntityById(user.getId());
		userEntity.assertMobileNumberConfirmed();

		Buddy buddyEntity = Buddy.getRepository().findOne(buddy.getId());
		userEntity.addBuddy(buddyEntity);
		User.getRepository().save(userEntity);

		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		userAnonymizedEntity.addBuddyAnonymized(buddyEntity.getBuddyAnonymized());
		userAnonymizedService.updateUserAnonymized(userEntity.getUserAnonymizedId(), userAnonymizedEntity);
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
	@Transactional
	public User getUserEntityById(UUID id)
	{
		if (id == null)
		{
			throw InvalidDataException.emptyUserId();
		}

		User entity = User.getRepository().findOne(id);

		if (entity == null)
		{
			throw UserServiceException.notFoundById(id);
		}

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
	public User getValidatedUserbyId(UUID id)
	{
		User retVal = getUserEntityById(id);
		retVal.assertMobileNumberConfirmed();

		return retVal;
	}

	private ConfirmationCode createConfirmationCode()
	{
		return ConfirmationCode.createInstance(generateConfirmationCode());
	}

	public String generateConfirmationCode()
	{
		return (yonaProperties.getSms().isEnabled())
				? CryptoUtil.getRandomDigits(yonaProperties.getSecurity().getConfirmationCodeDigits()) : "1234";
	}

	public void sendConfirmationCodeTextMessage(String mobileNumber, ConfirmationCode confirmationCode, String templateName)
	{
		Map<String, Object> templateParams = new HashMap<>();
		templateParams.put("confirmationCode", confirmationCode.getConfirmationCode());
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

	private void handleBuddyUsersRemovedWhileOffline(User user)
	{
		Set<Buddy> buddiesOfRemovedUsers = user.getBuddiesRelatedToRemovedUsers();
		buddiesOfRemovedUsers.stream().forEach(b -> buddyService.removeBuddyInfoForRemovedUser(user, b));
	}

	public void registerFailedAttempt(User userEntity, ConfirmationCode confirmationCode)
	{
		transactionHelper.executeInNewTransaction(() -> {
			confirmationCode.incrementAttempts();
			User.getRepository().save(userEntity);
		});
	}

	private User saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDto userResource)
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

	private void validateUserFields(UserDto userResource)
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

	public UUID getUserAnonymizedId(UUID userId)
	{
		return getPrivateUser(userId).getPrivateData().getUserAnonymizedId();
	}
}
