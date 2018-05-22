/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.ArrayList;
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

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.entities.ActivityCategoryRepository;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyRepository;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.Require;
import nu.yona.server.util.TimeUtil;

@Service
public class UserService
{
	/**
	 * Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers
	 */
	private static final Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	/**
	 * Holds the regex to validate a valid email address. Match the pattern a@b.c
	 */
	private static final Pattern REGEX_EMAIL = Pattern.compile("^[A-Z0-9._-]+@[A-Z0-9.-]+\\.[A-Z0-9.-]+$",
			Pattern.CASE_INSENSITIVE);

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

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired(required = false)
	private SmsService smsService;

	@Autowired(required = false)
	private BuddyService buddyService;

	@Autowired(required = false)
	private DeviceService deviceService;

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

	@Autowired(required = false)
	private LockPool<UUID> userSynchronizer;

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
		return createUserDtoWithPrivateData(getPrivateValidatedUserEntity(id));
	}

	@Transactional
	public User getPrivateValidatedUserEntity(UUID id)
	{
		User validatedUser = getValidatedUserById(id);
		return handlePrivateDataActions(validatedUser);
	}

	private User handlePrivateDataActions(User user)
	{
		handleBuddyUsersRemovedWhileOffline(user);

		if (!privateUserDataMigrationService.isUpToDate(user))
		{
			updateUser(user.getId(), privateUserDataMigrationService::upgrade);
		}

		return user;
	}

	@Transactional
	public void setOverwriteUserConfirmationCode(String mobileNumber)
	{
		updateUser(findUserByMobileNumber(mobileNumber).getId(), existingUserEntity -> {
			ConfirmationCode confirmationCode = createConfirmationCode();
			existingUserEntity.setOverwriteUserConfirmationCode(confirmationCode);
			sendConfirmationCodeTextMessage(mobileNumber, confirmationCode, SmsTemplate.OVERWRITE_USER_CONFIRMATION);
			logger.info("User with mobile number '{}' and ID '{}' requested an account overwrite confirmation code",
					existingUserEntity.getMobileNumber(), existingUserEntity.getId());
		});
	}

	@Transactional(dontRollbackOn = UserOverwriteConfirmationException.class)
	public UserDto addUser(UserDto user, Optional<String> overwriteUserConfirmationCode)
	{
		assert user.getPrivateData().getDevices().orElse(Collections.emptySet()).size() == 1;
		assertValidUserFields(user, UserPurpose.USER);

		handleExistingUserForMobileNumber(user.getMobileNumber(), overwriteUserConfirmationCode);

		User userEntity = createUserEntity(user, UserSignUp.FREE);
		addDevicesToEntity(user, userEntity);
		Optional<ConfirmationCode> confirmationCode = createConfirmationCodeIfNeeded(overwriteUserConfirmationCode, userEntity);
		userEntity = userRepository.save(userEntity);
		UserDto userDto = createUserDtoWithPrivateData(userEntity);
		if (confirmationCode.isPresent())
		{
			sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
					SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
		}

		logger.info("Added new user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
		return userDto;
	}

	private User createUserEntity(UserDto user, UserSignUp signUp)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		Set<Goal> goals = buildGoalsSet(user, signUp);
		UserAnonymized userAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(), goals);
		UserAnonymized.getRepository().save(userAnonymized);
		UserPrivate userPrivate = UserPrivate.createInstance(user.getOwnPrivateData().getFirstName(),
				user.getOwnPrivateData().getLastName(), user.getOwnPrivateData().getNickname(), userAnonymized.getId(),
				anonymousMessageSource.getId(), namedMessageSource);
		User userEntity = new User(UUID.randomUUID(), initializationVector, user.getMobileNumber(), userPrivate,
				namedMessageSource.getDestination());
		addMandatoryGoals(userEntity);
		if (signUp == UserSignUp.FREE)
		{
			// The user signs up through the app, so they apparently opened it now
			userEntity.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate());
		}
		return userEntity;
	}

	private void addDevicesToEntity(UserDto userDto, User userEntity)
	{
		userRepository.saveAndFlush(userEntity); // To prevent sequence issues when saving the device
		userDto.getOwnPrivateData().getDevices().orElse(Collections.emptySet()).stream().map(d -> (UserDeviceDto) d)
				.forEach(d -> deviceService.addDeviceToUser(userEntity, d));
	}

	private Set<Goal> buildGoalsSet(UserDto user, UserSignUp signUp)
	{
		Set<Goal> goals;
		if (signUp == UserSignUp.INVITED)
		{
			goals = Collections.emptySet();
		}
		else
		{
			goals = user.getOwnPrivateData().getGoals().orElse(Collections.emptySet()).stream().map(GoalDto::createGoalEntity)
					.collect(Collectors.toSet());
		}
		return goals;
	}

	private Optional<ConfirmationCode> createConfirmationCodeIfNeeded(Optional<String> overwriteUserConfirmationCode,
			User userEntity)
	{
		if (overwriteUserConfirmationCode.isPresent())
		{
			// no need to confirm again
			userEntity.markMobileNumberConfirmed();
			return Optional.empty();
		}

		Optional<ConfirmationCode> confirmationCode = Optional.of(createConfirmationCode());
		userEntity.setMobileNumberConfirmationCode(confirmationCode.get());
		return confirmationCode;
	}

	UserDto createUserDtoWithPrivateData(User user)
	{
		return UserDto.createInstanceWithPrivateData(user, buddyService.getBuddyDtos(user.getBuddies()));
	}

	private void addMandatoryGoals(User userEntity)
	{
		activityCategoryService.getAllActivityCategories().stream().filter(ActivityCategoryDto::isMandatoryNoGo)
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
		Require.that(currentNumberOfUsers < maxUsers, UserServiceException::maximumNumberOfUsersReached);
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
				r -> UserOverwriteConfirmationException.confirmationCodeMismatch(existingUserEntity.getMobileNumber(),
						userProvidedConfirmationCode, r),
				() -> UserOverwriteConfirmationException.tooManyAttempts(existingUserEntity.getMobileNumber()));

		// notice we can't delete the associated anonymized data
		// because the anonymized data cannot be retrieved
		// (the relation is encrypted, the password is not available)
		userRepository.delete(existingUserEntity);
		userRepository.flush(); // So we can insert another one
		logger.info("User with mobile number '{}' and ID '{}' removed, to overwrite the account",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional(dontRollbackOn = MobileNumberConfirmationException.class)
	public UserDto confirmMobileNumber(UUID userId, String userProvidedConfirmationCode)
	{
		User updatedUserEntity = updateUser(userId, userEntity -> {
			ConfirmationCode confirmationCode = userEntity.getMobileNumberConfirmationCode();

			assertValidConfirmationCode(userEntity, confirmationCode, userProvidedConfirmationCode,
					() -> MobileNumberConfirmationException.confirmationCodeNotSet(userEntity.getMobileNumber()),
					r -> MobileNumberConfirmationException.confirmationCodeMismatch(userEntity.getMobileNumber(),
							userProvidedConfirmationCode, r),
					() -> MobileNumberConfirmationException.tooManyAttempts(userEntity.getMobileNumber()));

			if (userEntity.isMobileNumberConfirmed())
			{
				throw MobileNumberConfirmationException.mobileNumberAlreadyConfirmed(userEntity.getMobileNumber());
			}

			userEntity.setMobileNumberConfirmationCode(null);
			userEntity.markMobileNumberConfirmed();

			logger.info("User with mobile number '{}' and ID '{}' successfully confirmed their mobile number",
					userEntity.getMobileNumber(), userEntity.getId());
		});
		return createUserDtoWithPrivateData(updatedUserEntity);
	}

	@Transactional
	public void resendMobileNumberConfirmationCode(UUID userId)
	{
		updateUser(userId, userEntity -> {
			logger.info("User with mobile number '{}' and ID '{}' requests to resend the mobile number confirmation code",
					userEntity.getMobileNumber(), userEntity.getId());
			ConfirmationCode confirmationCode = createConfirmationCode();
			userEntity.setMobileNumberConfirmationCode(confirmationCode);
			sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode,
					SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
		});
	}

	@Transactional
	UserDto addUserCreatedOnBuddyRequest(UserDto buddyUserResource)
	{
		assertUserIsAllowed(buddyUserResource.getMobileNumber(), UserSignUp.INVITED);
		User newUser = createUserEntity(buddyUserResource, UserSignUp.INVITED);
		newUser.setIsCreatedOnBuddyRequest();
		newUser.setMobileNumberConfirmationCode(createConfirmationCode());
		User savedUser = userRepository.save(newUser);
		logger.info("User with mobile number '{}' and ID '{}' created on buddy request", savedUser.getMobileNumber(),
				savedUser.getId());
		return createUserDtoWithPrivateData(savedUser);
	}

	@Transactional
	public UserDto updateUser(UUID id, UserDto user)
	{
		User updatedUserEntity = updateUser(id, userEntity -> {
			UserDto originalUser = createUserDtoWithPrivateData(userEntity);
			assertValidUpdateRequest(user, originalUser, userEntity);

			user.updateUser(userEntity);
			Optional<ConfirmationCode> confirmationCode = createConfirmationCodeIfNumberUpdated(user, originalUser, userEntity);
			if (confirmationCode.isPresent())
			{
				sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
						SmsTemplate.CHANGED_USER_NUMBER_CONFIRMATION);
			}
			UserDto userDto = createUserDtoWithPrivateData(userEntity);
			logger.info("Updated user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
			buddyService.broadcastUserInfoChangeToBuddies(userEntity, originalUser);
		});
		return createUserDtoWithPrivateData(updatedUserEntity);
	}

	/**
	 * Performs the given update action on the user with the specified ID, while holding a write-lock on the user. After the
	 * update, the entity is saved to the repository.<br/>
	 * We are using pessimistic locking because we generally do not have update concurrency, except when performing migration
	 * steps. Migration steps are executed during GET-requests and GET-requests can come concurrently. Optimistic locking wouldn't
	 * be an option here as that would cause the GETs to fail rather than to wait for the other one to complete.
	 * 
	 * @param id The ID of the user to update
	 * @param updateAction The update action to perform
	 * @return The updated and saved user
	 */
	@Transactional
	public User updateUser(UUID id, Consumer<User> updateAction)
	{
		// We add a lock here to prevent concurrent user updates.
		// The lock is per user, so concurrency is not an issue.
		return withLockOnUser(id, user -> {
			updateAction.accept(user);
			if (user.canAccessPrivateData())
			{
				// The private data is accessible and might be updated, including the UserAnonymized
				// Let the UserAnonymizedService save that to the repository and cache it
				userAnonymizedService.updateUserAnonymized(user.getAnonymized());
			}
			return userRepository.save(user);
		});
	}

	private User withLockOnUser(UUID userId, Function<User, User> action)
	{
		try (LockPool<UUID>.Lock lock = userSynchronizer.lock(userId))
		{
			User user = getUserEntityById(userId);
			return action.apply(user);
		}
	}

	@Transactional
	public UserDto updateUserPhoto(UUID id, Optional<UUID> userPhotoId)
	{
		User updatedUserEntity = updateUser(id, userEntity -> {
			UserDto originalUser = createUserDtoWithPrivateData(userEntity);
			userEntity.setUserPhotoId(userPhotoId);
			UserDto userDto = createUserDtoWithPrivateData(userEntity);
			if (userPhotoId.isPresent())
			{
				logger.info("Updated user photo for user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(),
						userDto.getId());
			}
			else
			{
				logger.info("Deleted user photo for user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(),
						userDto.getId());
			}
			buddyService.broadcastUserInfoChangeToBuddies(userEntity, originalUser);
		});
		return createUserDtoWithPrivateData(updatedUserEntity);
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

	private Optional<ConfirmationCode> createConfirmationCodeIfNumberUpdated(UserDto user, UserDto originalUser,
			User updatedUserEntity)
	{
		if (isMobileNumberDifferent(user, originalUser))
		{
			Optional<ConfirmationCode> confirmationCode = Optional.of(createConfirmationCode());
			updatedUserEntity.setMobileNumberConfirmationCode(confirmationCode.get());
			return confirmationCode;
		}
		return Optional.empty();
	}

	private boolean isMobileNumberDifferent(UserDto user, UserDto originalUser)
	{
		return !user.getMobileNumber().equals(originalUser.getMobileNumber());
	}

	@Transactional
	public UserDto updateUserCreatedOnBuddyRequest(UUID id, String tempPassword, UserDto user)
	{
		User originalUserEntity = getUserEntityById(id);
		// security check: should not be able to replace the password on an existing user
		Require.that(originalUserEntity.isCreatedOnBuddyRequest(), () -> UserServiceException.userNotCreatedOnBuddyRequest(id));

		EncryptedUserData retrievedEntitySet = retrieveUserEncryptedData(originalUserEntity, tempPassword);
		User savedUserEntity = saveUserEncryptedDataWithNewPassword(retrievedEntitySet, user);
		sendConfirmationCodeTextMessage(savedUserEntity.getMobileNumber(), savedUserEntity.getMobileNumberConfirmationCode(),
				SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
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
		withLockOnUser(id, userEntity -> deleteUserInsideLock(userEntity, message));
	}

	private User deleteUserInsideLock(User userEntity, Optional<String> message)
	{
		deleteBuddyInfo(userEntity, message);

		UUID userAnonymizedId = userEntity.getUserAnonymizedId();
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId);
		User updatedUserEntity = deleteMessageSources(userEntity, userAnonymizedEntity);

		Set<Goal> allGoalsIncludingHistoryItems = getAllGoalsIncludingHistoryItems(updatedUserEntity);
		deleteActivitiesWithTheirComments(userAnonymizedEntity, allGoalsIncludingHistoryItems);
		deleteGoals(userAnonymizedEntity, allGoalsIncludingHistoryItems);
		deleteDevices(userEntity);

		userAnonymizedService.deleteUserAnonymized(userAnonymizedId);
		userRepository.delete(updatedUserEntity);

		logger.info("Deleted user with mobile number '{}' and ID '{}'", updatedUserEntity.getMobileNumber(),
				updatedUserEntity.getId());

		return null; // Need to return something, but it isn't used
	}

	private void deleteBuddyInfo(User userEntity, Optional<String> message)
	{
		buddyService.processPossiblePendingBuddyResponseMessages(userEntity);

		handleBuddyUsersRemovedWhileOffline(userEntity);

		userEntity.getBuddies().forEach(buddyEntity -> buddyService.removeBuddyInfoForBuddy(userEntity, buddyEntity, message,
				DropBuddyReason.USER_ACCOUNT_DELETED));
	}

	private void deleteDevices(User userEntity)
	{
		new ArrayList<>(userEntity.getDevices()).stream()
				.forEach(d -> deviceService.deleteDeviceWithoutBuddyNotification(userEntity, d));
	}

	private void deleteGoals(UserAnonymized userAnonymizedEntity, Set<Goal> allGoalsIncludingHistoryItems)
	{
		allGoalsIncludingHistoryItems.forEach(Goal::removeAllWeekActivities);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
	}

	private void deleteActivitiesWithTheirComments(UserAnonymized userAnonymizedEntity, Set<Goal> allGoalsIncludingHistoryItems)
	{
		deleteAllWeekActivityCommentMessages(allGoalsIncludingHistoryItems);
		deleteAllDayActivitiesWithTheirCommentMessages(allGoalsIncludingHistoryItems);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
	}

	private User deleteMessageSources(User userEntity, UserAnonymized userAnonymizedEntity)
	{
		MessageSource namedMessageSource = messageSourceRepository.findOne(userEntity.getNamedMessageSourceId());
		MessageSource anonymousMessageSource = messageSourceRepository.findOne(userEntity.getAnonymousMessageSourceId());
		userAnonymizedEntity.clearAnonymousDestination();
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		userEntity.clearNamedMessageDestination();
		User updatedUserEntity = userRepository.saveAndFlush(userEntity);

		messageSourceRepository.delete(anonymousMessageSource);
		messageSourceRepository.delete(namedMessageSource);
		messageSourceRepository.flush();
		return updatedUserEntity;
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
		Require.that(user != null && user.getId() != null, InvalidDataException::emptyUserId);
		Require.that(buddy != null && buddy.getId() != null, InvalidDataException::emptyBuddyId);

		updateUser(user.getId(), userEntity -> {
			assertValidatedUser(userEntity);

			Buddy buddyEntity = buddyRepository.findOne(buddy.getId());
			userEntity.addBuddy(buddyEntity);

			UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
			userAnonymizedEntity.addBuddyAnonymized(buddyEntity.getBuddyAnonymized());
			userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		});
	}

	public String generatePassword()
	{
		return CryptoUtil.getRandomString(yonaProperties.getSecurity().getPasswordLength());
	}

	static User findUserByMobileNumber(String mobileNumber)
	{
		User userEntity = User.getRepository().findByMobileNumber(mobileNumber);
		Require.isNonNull(userEntity, () -> UserServiceException.notFoundByMobileNumber(mobileNumber));
		return userEntity;
	}

	public static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(UserServiceException::missingPasswordHeader);
	}

	/**
	 * This method returns a user entity. The passed on Id is checked whether or not it is set. it also checks that the return
	 * value is always the user entity. If not an exception is thrown. The entity is fetched without lock.
	 * 
	 * @param id the ID of the user
	 * @return The user entity (never null)
	 */
	@Transactional
	public User getUserEntityById(UUID id)
	{
		Require.isNonNull(id, InvalidDataException::emptyUserId);

		User entity = userRepository.findOne(id);

		Require.isNonNull(entity, () -> UserServiceException.notFoundById(id));

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
	public User getValidatedUserById(UUID id)
	{
		User retVal = getUserEntityById(id);
		assertValidatedUser(retVal);

		return retVal;
	}

	public void assertValidatedUser(User user)
	{
		user.assertMobileNumberConfirmed();
	}

	private ConfirmationCode createConfirmationCode()
	{
		return ConfirmationCode.createInstance(generateConfirmationCode());
	}

	public String generateConfirmationCode()
	{
		return (yonaProperties.getSms().isEnabled())
				? CryptoUtil.getRandomDigits(yonaProperties.getSecurity().getConfirmationCodeDigits())
				: "1234";
	}

	public void sendConfirmationCodeTextMessage(String mobileNumber, ConfirmationCode confirmationCode, SmsTemplate template)
	{
		Map<String, Object> templateParams = new HashMap<>();
		templateParams.put("confirmationCode", confirmationCode.getCode());
		smsService.send(mobileNumber, template, templateParams);
	}

	private void assertValidConfirmationCode(User userEntity, ConfirmationCode confirmationCode,
			String userProvidedConfirmationCode, Supplier<YonaException> noConfirmationCodeExceptionSupplier,
			Function<Integer, YonaException> invalidConfirmationCodeExceptionSupplier,
			Supplier<YonaException> tooManyAttemptsExceptionSupplier)
	{
		Require.isNonNull(confirmationCode, noConfirmationCodeExceptionSupplier);

		int remainingAttempts = yonaProperties.getSecurity().getConfirmationCodeMaxAttempts() - confirmationCode.getAttempts();
		if (remainingAttempts <= 0)
		{
			throw tooManyAttemptsExceptionSupplier.get();
		}

		if (!confirmationCode.getCode().equals(userProvidedConfirmationCode))
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
		updateUser(userEntity.getId(), u -> confirmationCode.incrementAttempts());
	}

	private User saveUserEncryptedDataWithNewPassword(EncryptedUserData retrievedEntitySet, UserDto user)
	{
		return updateUser(retrievedEntitySet.userEntity.getId(), userEntity -> {
			assert user.getPrivateData().getDevices().orElse(Collections.emptySet()).size() == 1;
			// touch and save all user related data containing encryption
			// see architecture overview for which classes contain encrypted data
			// (this could also be achieved with very complex reflection)
			userEntity.getBuddies().forEach(buddy -> buddyRepository.save(buddy.touch()));
			messageSourceRepository.save(retrievedEntitySet.namedMessageSource.touch());
			messageSourceRepository.save(retrievedEntitySet.anonymousMessageSource.touch());
			user.updateUser(userEntity);
			userEntity.unsetIsCreatedOnBuddyRequest();
			userEntity.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate());
			addDevicesToEntity(user, userEntity);
			userEntity.touch();
		});
	}

	void assertValidUserFields(UserDto user, UserPurpose purpose)
	{
		Require.that(StringUtils.isNotBlank(user.getOwnPrivateData().getFirstName()), InvalidDataException::blankFirstName);
		Require.that(StringUtils.isNotBlank(user.getOwnPrivateData().getLastName()), InvalidDataException::blankLastName);
		Require.that(!(purpose == UserPurpose.USER && StringUtils.isBlank(user.getOwnPrivateData().getNickname())),
				InvalidDataException::blankNickname);

		Require.that(StringUtils.isNotBlank(user.getMobileNumber()), InvalidDataException::blankMobileNumber);

		assertValidMobileNumber(user.getMobileNumber());

		if (purpose == UserPurpose.BUDDY)
		{
			Require.that(StringUtils.isNotBlank(user.getEmailAddress()), InvalidDataException::blankEmailAddress);
			assertValidEmailAddress(user.getEmailAddress());

			Require.that(user.getOwnPrivateData().getGoals().orElse(Collections.emptySet()).isEmpty(),
					InvalidDataException::goalsNotSupported);
		}
		else
		{
			Require.that(StringUtils.isBlank(user.getEmailAddress()), InvalidDataException::excessEmailAddress);
		}
	}

	public void assertValidMobileNumber(String mobileNumber)
	{
		Require.that(REGEX_PHONE.matcher(mobileNumber).matches(), () -> InvalidDataException.invalidMobileNumber(mobileNumber));
		assertNumberIsMobile(mobileNumber);
	}

	private void assertNumberIsMobile(String mobileNumber)
	{
		try
		{
			PhoneNumberUtil util = PhoneNumberUtil.getInstance();
			PhoneNumberType numberType = util.getNumberType(util.parse(mobileNumber, null));
			if ((numberType != PhoneNumberType.MOBILE) && (numberType != PhoneNumberType.FIXED_LINE_OR_MOBILE))
			{
				throw InvalidDataException.notAMobileNumber(mobileNumber, numberType.toString());
			}
		}
		catch (NumberParseException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public void assertValidEmailAddress(String emailAddress)
	{
		Require.that(REGEX_EMAIL.matcher(emailAddress).matches(), () -> InvalidDataException.invalidEmailAddress(emailAddress));
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
		return getUserEntityById(userId).getUserAnonymizedId();
	}
}
