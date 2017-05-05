/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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
import nu.yona.server.goals.entities.ActivityCategoryRepository;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyRepository;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;
import nu.yona.server.util.TimeUtil;
import nu.yona.server.util.TransactionHelper;

@Service
public class UserService
{
	/** Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers */
	private static Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	/** Holds the regex to validate a valid email address. Match the pattern a@b.c */
	private static Pattern REGEX_EMAIL = Pattern.compile("^[A-Z0-9._-]+@[A-Z0-9.-]+\\.[A-Z0-9.-]+$", Pattern.CASE_INSENSITIVE);

	private enum UserSignUp
	{
		INVITED, FREE
	}

	enum UserPurpose
	{
		USER, BUDDY
	}

	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

	@Autowired(required = false)
	private UserRepository userRepository;

	@Autowired(required = false)
	private MessageSourceRepository messageSourceRepository;

	@Autowired(required = false)
	private BuddyRepository buddyRepository;

	@Autowired(required = false)
	private ActivityCategoryRepository activityCategoryRepository;

	@Autowired(required = false)
	private LDAPUserService ldapUserService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired(required = false)
	private SmsService smsService;

	@Autowired(required = false)
	private BuddyService buddyService;

	@Autowired(required = false)
	private TransactionHelper transactionHelper;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private ActivityCategoryService activityCategoryService;

	@Autowired(required = false)
	private MessageService messageService;

	@Autowired(required = false)
	private WhiteListedNumberService whiteListedNumberService;

	@Autowired(required = false)
	private PrivateUserDataMigrationService privateUserDataMigrationService;

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
		return getPrivateUser(id, u -> { // Nothing required here
		});
	}

	@Transactional
	public UserDto getPrivateUser(UUID id, boolean isCreatedOnBuddyRequest)
	{
		return getPrivateUser(id, u -> assertCreatedOnBuddyRequestStatus(u, isCreatedOnBuddyRequest));
	}

	private UserDto getPrivateUser(UUID id, Consumer<User> userStatusAsserter)
	{
		User user = getUserEntityById(id);
		userStatusAsserter.accept(user);
		User updatedUser = handlePrivateDataActions(user);
		return createUserDtoWithPrivateData(updatedUser);
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
		User validatedUser = getValidatedUserbyId(id);
		User updatedUser = handlePrivateDataActions(validatedUser);
		return createUserDtoWithPrivateData(updatedUser);
	}

	private User handlePrivateDataActions(User user)
	{
		handleBuddyUsersRemovedWhileOffline(user);

		if (!privateUserDataMigrationService.isUpToDate(user))
		{
			privateUserDataMigrationService.upgrade(user);
			return userRepository.save(user);
		}

		return user;
	}

	@Transactional
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		ConfirmationCode confirmationCode = createConfirmationCode();
		existingUserEntity.setOverwriteUserConfirmationCode(confirmationCode);
		userRepository.save(existingUserEntity);
		sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, SmsService.TemplateName_OverwriteUserConfirmation);
		logger.info("User with mobile number '{}' and ID '{}' requested an account overwrite confirmation code",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional
	public UserDto addUser(UserDto user, Optional<String> overwriteUserConfirmationCode)
	{
		assertValidUserFields(user, UserPurpose.USER);

		// use a separate transaction because in the top transaction we insert a user with the same unique key
		transactionHelper.executeInNewTransaction(
				() -> handleExistingUserForMobileNumber(user.getMobileNumber(), overwriteUserConfirmationCode));

		User userEntity = createUserEntity(user, UserSignUp.FREE);
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
		userEntity = userRepository.save(userEntity);
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

	private User createUserEntity(UserDto user, UserSignUp signUp)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		Set<Goal> goals;
		if (signUp == UserSignUp.INVITED)
		{
			goals = Collections.emptySet();
		}
		else
		{
			goals = user.getPrivateData().getGoals().stream().map(g -> g.createGoalEntity()).collect(Collectors.toSet());
		}
		UserAnonymized userAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(), goals);
		UserAnonymized.getRepository().save(userAnonymized);
		UserPrivate userPrivate = UserPrivate.createInstance(user.getPrivateData().getNickname(), generatePassword(),
				userAnonymized.getId(), anonymousMessageSource.getId(), namedMessageSource);
		User userEntity = new User(UUID.randomUUID(), initializationVector, user.getFirstName(), user.getLastName(),
				user.getMobileNumber(), userPrivate, namedMessageSource.getDestination());
		addMandatoryGoals(userEntity);
		if (signUp == UserSignUp.FREE)
		{
			// The user signs up through the app, so they apparently opened it now
			userEntity.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate());
		}
		return userEntity;
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
		userEntity.getAnonymized()
				.addGoal(BudgetGoal.createNoGoInstance(TimeUtil.utcNow(), activityCategoryRepository.findOne(category.getId())));
	}

	private void handleExistingUserForMobileNumber(String mobileNumber, Optional<String> overwriteUserConfirmationCode)
	{
		if (overwriteUserConfirmationCode.isPresent())
		{
			deleteExistingUserToOverwriteIt(mobileNumber, overwriteUserConfirmationCode.get());
		}
		else
		{
			assertUserDoesNotExist(mobileNumber);
			assertUserIsAllowed(mobileNumber, UserSignUp.FREE);
		}
	}

	private void assertUserDoesNotExist(String mobileNumber)
	{
		User existingUser = userRepository.findByMobileNumber(mobileNumber);
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

	private void assertUserIsAllowed(String mobileNumber, UserSignUp origin)
	{
		assertBelowMaxUsers();
		assertWhiteList(mobileNumber, origin);
	}

	private void assertBelowMaxUsers()
	{
		int maxUsers = yonaProperties.getMaxUsers();
		long currentNumberOfUsers = userRepository.count();
		if (currentNumberOfUsers >= maxUsers)
		{
			throw UserServiceException.maximumNumberOfUsersReached();
		}
		if (currentNumberOfUsers >= maxUsers - maxUsers / 10)
		{
			logger.warn("Nearing the maximum number of users. Current number: {}, maximum: {}", currentNumberOfUsers, maxUsers);
		}
	}

	private void assertWhiteList(String mobileNumber, UserSignUp origin)
	{
		if ((origin == UserSignUp.FREE && yonaProperties.isWhiteListActiveFreeSignUp())
				|| (origin == UserSignUp.INVITED && yonaProperties.isWhiteListActiveInvitedUsers()))
		{
			whiteListedNumberService.assertMobileNumberIsAllowed(mobileNumber);
		}
	}

	private void deleteExistingUserToOverwriteIt(String mobileNumber, String userProvidedConfirmationCode)
	{
		User existingUserEntity = findUserByMobileNumber(mobileNumber);
		ConfirmationCode confirmationCode = existingUserEntity.getOverwriteUserConfirmationCode();

		assertValidConfirmationCode(existingUserEntity, confirmationCode, userProvidedConfirmationCode,
				() -> UserOverwriteConfirmationException.confirmationCodeNotSet(existingUserEntity.getMobileNumber()),
				(r) -> UserOverwriteConfirmationException.confirmationCodeMismatch(existingUserEntity.getMobileNumber(),
						userProvidedConfirmationCode, r),
				() -> UserOverwriteConfirmationException.tooManyAttempts(existingUserEntity.getMobileNumber()));

		// notice we can't delete the associated anonymized data
		// because the anonymized data cannot be retrieved
		// (the relation is encrypted, the password is not available)
		userRepository.delete(existingUserEntity);
		logger.info("User with mobile number '{}' and ID '{}' removed, to overwrite the account",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional
	public UserDto confirmMobileNumber(UUID userId, String userProvidedConfirmationCode)
	{
		User userEntity = getUserEntityById(userId);
		ConfirmationCode confirmationCode = userEntity.getMobileNumberConfirmationCode();

		assertValidConfirmationCode(userEntity, confirmationCode, userProvidedConfirmationCode,
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
		userRepository.save(userEntity);

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
		userRepository.save(userEntity);
		sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
				SmsService.TemplateName_AddUserNumberConfirmation);
		return null;
	}

	@Transactional
	public void postOpenAppEvent(UUID userId)
	{
		User userEntity = getUserEntityById(userId);
		LocalDate now = TimeUtil.utcNow().toLocalDate();
		Optional<LocalDate> appLastOpenedDate = userEntity.getAppLastOpenedDate();
		if (!appLastOpenedDate.isPresent() || appLastOpenedDate.get().isBefore(now))
		{
			userEntity.setAppLastOpenedDate(now);
			userRepository.save(userEntity);
		}
	}

	@Transactional
	User addUserCreatedOnBuddyRequest(UserDto buddyUserResource)
	{
		assertUserIsAllowed(buddyUserResource.getMobileNumber(), UserSignUp.INVITED);
		User newUser = createUserEntity(buddyUserResource, UserSignUp.INVITED);
		newUser.setIsCreatedOnBuddyRequest();
		newUser.setMobileNumberConfirmationCode(createConfirmationCode());
		User savedUser = userRepository.save(newUser);
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
		assertValidUpdateRequest(user, originalUser, originalUserEntity);

		User updatedUserEntity = user.updateUser(originalUserEntity);
		Optional<ConfirmationCode> confirmationCode = Optional.empty();
		if (isMobileNumberDifferent(user, originalUser))
		{
			confirmationCode = Optional.of(createConfirmationCode());
			updatedUserEntity.setMobileNumberConfirmationCode(confirmationCode.get());
		}
		User savedUserEntity = userRepository.save(updatedUserEntity);
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

	private void assertValidUpdateRequest(UserDto user, UserDto originalUser, User originalUserEntity)
	{
		if (originalUserEntity.isCreatedOnBuddyRequest())
		{
			// security check: should not be able to update a user created on buddy request with its temp password
			throw UserServiceException.cannotUpdateBecauseCreatedOnBuddyRequest(user.getId());
		}
		if (isMobileNumberDifferent(user, originalUser))
		{
			assertUserDoesNotExist(user.getMobileNumber());
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
			throw UserServiceException.userNotCreatedOnBuddyRequest(id);
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
		MessageSource namedMessageSource = messageSourceRepository.findOne(originalUserEntity.getNamedMessageSourceId());
		MessageSource anonymousMessageSource = messageSourceRepository.findOne(originalUserEntity.getAnonymousMessageSourceId());
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
		MessageSource namedMessageSource = messageSourceRepository.findOne(userEntity.getNamedMessageSourceId());
		MessageSource anonymousMessageSource = messageSourceRepository.findOne(userEntity.getAnonymousMessageSourceId());
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId);
		userAnonymizedEntity.clearAnonymousDestination();
		userAnonymizedEntity = userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		userEntity.clearNamedMessageDestination();
		User updatedUserEntity = userRepository.saveAndFlush(userEntity);

		messageSourceRepository.delete(anonymousMessageSource);
		messageSourceRepository.delete(namedMessageSource);
		messageSourceRepository.flush();

		Set<Goal> allGoalsIncludingHistoryItems = getAllGoalsIncludingHistoryItems(updatedUserEntity);
		deleteAllWeekActivityCommentMessages(allGoalsIncludingHistoryItems);
		deleteAllDayActivitiesWithTheirCommentMessages(allGoalsIncludingHistoryItems);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);

		allGoalsIncludingHistoryItems.forEach(g -> g.removeAllWeekActivities());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);

		userAnonymizedService.deleteUserAnonymized(userAnonymizedId);
		userRepository.delete(updatedUserEntity);

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

		Buddy buddyEntity = buddyRepository.findOne(buddy.getId());
		userEntity.addBuddy(buddyEntity);
		userRepository.save(userEntity);

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

		User entity = userRepository.findOne(id);

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

	private void assertValidConfirmationCode(User userEntity, ConfirmationCode confirmationCode,
			String userProvidedConfirmationCode, Supplier<YonaException> noConfirmationCodeExceptionSupplier,
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
			userRepository.save(userEntity);
		});
	}

	private User saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDto userResource)
	{
		// touch and save all user related data containing encryption
		// see architecture overview for which classes contain encrypted data
		// (this could also be achieved with very complex reflection)
		retrievedEntitySet.userEntity.getBuddies().forEach(buddy -> buddyRepository.save(buddy.touch()));
		messageSourceRepository.save(retrievedEntitySet.namedMessageSource.touch());
		messageSourceRepository.save(retrievedEntitySet.anonymousMessageSource.touch());
		userResource.updateUser(retrievedEntitySet.userEntity);
		retrievedEntitySet.userEntity.unsetIsCreatedOnBuddyRequest();
		retrievedEntitySet.userEntity.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate());
		retrievedEntitySet.userEntity.touch();
		return userRepository.save(retrievedEntitySet.userEntity);
	}

	void assertValidUserFields(UserDto userResource, UserPurpose userPurpose)
	{
		if (StringUtils.isBlank(userResource.getFirstName()))
		{
			throw InvalidDataException.blankFirstName();
		}

		if (StringUtils.isBlank(userResource.getLastName()))
		{
			throw InvalidDataException.blankLastName();
		}

		if (userPurpose == UserPurpose.USER && StringUtils.isBlank(userResource.getPrivateData().getNickname()))
		{
			throw InvalidDataException.blankNickname();
		}

		if (StringUtils.isBlank(userResource.getMobileNumber()))
		{
			throw InvalidDataException.blankMobileNumber();
		}

		assertValidMobileNumber(userResource.getMobileNumber());

		if (userPurpose == UserPurpose.BUDDY)
		{
			if (StringUtils.isBlank(userResource.getEmailAddress()))
			{
				throw InvalidDataException.blankEmailAddress();
			}
			assertValidEmailAddress(userResource.getEmailAddress());

			if (!userResource.getPrivateData().getGoals().isEmpty())
			{
				throw InvalidDataException.goalsNotSupported();
			}
		}
		else
		{
			if (!StringUtils.isBlank(userResource.getEmailAddress()))
			{
				throw InvalidDataException.excessEmailAddress();
			}
		}
	}

	public void assertValidMobileNumber(String mobileNumber)
	{
		if (!REGEX_PHONE.matcher(mobileNumber).matches())
		{
			throw InvalidDataException.invalidMobileNumber(mobileNumber);
		}
	}

	public void assertValidEmailAddress(String emailAddress)
	{
		if (!REGEX_EMAIL.matcher(emailAddress).matches())
		{
			throw InvalidDataException.invalidEmailAddress(emailAddress);
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
