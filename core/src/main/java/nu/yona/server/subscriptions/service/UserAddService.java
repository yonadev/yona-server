/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.exceptions.UserOverwriteConfirmationException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.util.Require;
import nu.yona.server.util.TimeUtil;

@Service
public class UserAddService
{
	private enum UserSignUp
	{
		INVITED, FREE
	}

	private static final Logger logger = LoggerFactory.getLogger(UserAddService.class);

	@Autowired(required = false)
	private UserRepository userRepository;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired(required = false)
	private DeviceService deviceService;

	@Autowired(required = false)
	private ActivityCategoryService activityCategoryService;

	@Autowired(required = false)
	private WhiteListedNumberService whiteListedNumberService;

	@Autowired(required = false)
	private UserAssertionService userAssertionService;

	@Autowired(required = false)
	private UserLookupService userLookupService;

	@Autowired(required = false)
	private UserUpdateService userUpdateService;

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

	@Transactional(dontRollbackOn = UserOverwriteConfirmationException.class)
	public UserDto addUser(UserDto user, Optional<String> overwriteUserConfirmationCode)
	{
		assertIsValidNewUser(user);

		handleExistingUserForMobileNumber(user.getMobileNumber(), overwriteUserConfirmationCode);

		User userEntity = createUserEntity(user, UserSignUp.FREE);
		addDevicesToEntity(user, userEntity);
		Optional<ConfirmationCode> confirmationCode = createConfirmationCodeIfNeeded(overwriteUserConfirmationCode, userEntity);
		userEntity = userRepository.save(userEntity);
		UserDto userDto = userLookupService.createUserDto(userEntity);
		if (confirmationCode.isPresent())
		{
			userUpdateService.sendConfirmationCodeTextMessage(userEntity.getMobileNumber(), confirmationCode.get(),
					SmsTemplate.ADD_USER_NUMBER_CONFIRMATION);
		}

		logger.info("Added new user with mobile number '{}' and ID '{}'", userDto.getMobileNumber(), userDto.getId());
		return userDto;
	}

	private void assertIsValidNewUser(UserDto user)
	{
		Require.that(user.getPrivateData().getDevices().orElse(Collections.emptySet()).size() == 1,
				() -> YonaException.illegalState("Number of devices must be 1"));
		userAssertionService.assertValidUserFields(user, UserService.UserPurpose.USER);
		Require.that(user.getCreationTime().isEmpty() || yonaProperties.isTestServer(),
				() -> InvalidDataException.onlyAllowedOnTestServers("Cannot set user creation time"));
	}

	private User createUserEntity(UserDto user, UserSignUp signUp)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		Set<Goal> goals = buildGoalsSet(user, signUp);
		UserAnonymized userAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(), goals);
		UserAnonymized.getRepository().save(userAnonymized);
		UserPrivate userPrivate = UserPrivate
				.createInstance(user.getCreationTime().orElse(TimeUtil.utcNow()), user.getOwnPrivateData().getFirstName(),
						user.getOwnPrivateData().getLastName(), user.getOwnPrivateData().getNickname(), userAnonymized.getId(),
						anonymousMessageSource.getId(), namedMessageSource);
		User userEntity = new User(UUID.randomUUID(), initializationVector,
				user.getCreationTime().map(LocalDateTime::toLocalDate).orElse(TimeUtil.utcNow().toLocalDate()),
				user.getMobileNumber(), userPrivate, namedMessageSource.getDestination());
		addMandatoryGoals(userEntity);
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
			goals = user.getOwnPrivateData().getGoalsIncludingHistoryItems().orElse(Collections.emptySet()).stream()
					.map(GoalDto::createGoalEntity).collect(Collectors.toSet());
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

		ConfirmationCode confirmationCode = userUpdateService.createConfirmationCode();
		userEntity.setMobileNumberConfirmationCode(confirmationCode);
		return Optional.of(confirmationCode);
	}

	private void addMandatoryGoals(User userEntity)
	{
		activityCategoryService.getAllActivityCategories().stream().filter(ActivityCategoryDto::isMandatoryNoGo)
				.forEach(c -> addNoGoGoal(userEntity, c));
	}

	private void addNoGoGoal(User userEntity, ActivityCategoryDto category)
	{
		userEntity.getAnonymized().addGoal(BudgetGoal
				.createNoGoInstance(TimeUtil.utcNow(), activityCategoryService.getActivityCategoryEntity(category.getId())));
	}

	private void handleExistingUserForMobileNumber(String mobileNumber, Optional<String> overwriteUserConfirmationCode)
	{
		if (overwriteUserConfirmationCode.isPresent())
		{
			deleteExistingUserToOverwriteIt(mobileNumber, overwriteUserConfirmationCode.get());
		}
		else
		{
			userAssertionService.assertUserDoesNotExist(mobileNumber);
			assertUserIsAllowed(mobileNumber, UserSignUp.FREE);
		}
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
		if (currentNumberOfUsers >= maxUsers - 100)
		{
			logger.warn("Nearing the maximum number of users. Current number: {}, maximum: {}", currentNumberOfUsers, maxUsers);
		}
	}

	private void assertWhiteList(String mobileNumber, UserSignUp origin)
	{
		if ((origin == UserSignUp.FREE && yonaProperties.isWhiteListActiveFreeSignUp()) || (origin == UserSignUp.INVITED
				&& yonaProperties.isWhiteListActiveInvitedUsers()))
		{
			whiteListedNumberService.assertMobileNumberIsAllowed(mobileNumber);
		}
	}

	private void deleteExistingUserToOverwriteIt(String mobileNumber, String userProvidedConfirmationCode)
	{
		User existingUserEntity = userLookupService
				.lockUserForUpdate(UserLookupService.findUserByMobileNumber(mobileNumber).getId());
		Optional<ConfirmationCode> confirmationCode = existingUserEntity.getOverwriteUserConfirmationCode();

		assertValidConfirmationCode(existingUserEntity, confirmationCode, userProvidedConfirmationCode,
				() -> UserOverwriteConfirmationException.confirmationCodeNotSet(existingUserEntity.getMobileNumber()),
				r -> UserOverwriteConfirmationException
						.confirmationCodeMismatch(existingUserEntity.getMobileNumber(), userProvidedConfirmationCode, r),
				() -> UserOverwriteConfirmationException.tooManyAttempts(existingUserEntity.getMobileNumber()));

		// notice we can't delete the associated anonymized data
		// because the anonymized data cannot be retrieved
		// (the relation is encrypted, the password is not available)
		logger.info("DEBUG: mobile number {} delete/overwrite user with confirmation code ID {}", mobileNumber, confirmationCode.get().getId());
		userRepository.delete(existingUserEntity);
		userRepository.flush(); // So we can insert another one
		logger.info("User with mobile number '{}' and ID '{}' removed, to overwrite the account",
				existingUserEntity.getMobileNumber(), existingUserEntity.getId());
	}

	@Transactional
	UserDto addUserCreatedOnBuddyRequest(UserDto buddyUserResource)
	{
		assertUserIsAllowed(buddyUserResource.getMobileNumber(), UserSignUp.INVITED);
		User newUser = createUserEntity(buddyUserResource, UserSignUp.INVITED);
		newUser.setIsCreatedOnBuddyRequest();
		newUser.setMobileNumberConfirmationCode(userUpdateService.createConfirmationCode());
		User savedUser = userRepository.save(newUser);
		logger.info("User with mobile number '{}' and ID '{}' created on buddy request", savedUser.getMobileNumber(),
				savedUser.getId());
		return userLookupService.createUserDto(savedUser);
	}

	@Transactional(dontRollbackOn = { MobileNumberConfirmationException.class, UserOverwriteConfirmationException.class })
	void assertValidConfirmationCode(User userEntity, Optional<ConfirmationCode> confirmationCode,
			String userProvidedConfirmationCode, Supplier<YonaException> noConfirmationCodeExceptionSupplier,
			IntFunction<YonaException> invalidConfirmationCodeExceptionSupplier,
			Supplier<YonaException> tooManyAttemptsExceptionSupplier)
	{
		assertValidConfirmationCode(userEntity, confirmationCode.orElseThrow(noConfirmationCodeExceptionSupplier),
				userProvidedConfirmationCode, invalidConfirmationCodeExceptionSupplier, tooManyAttemptsExceptionSupplier);
	}

	private void assertValidConfirmationCode(User userEntity, ConfirmationCode confirmationCode,
			String userProvidedConfirmationCode, IntFunction<YonaException> invalidConfirmationCodeExceptionSupplier,
			Supplier<YonaException> tooManyAttemptsExceptionSupplier)
	{
		userAssertionService.assertUserEntityLockedForUpdate(userEntity);
		int remainingAttempts = yonaProperties.getSecurity().getConfirmationCodeMaxAttempts() - confirmationCode.getAttempts();
		if (remainingAttempts <= 0)
		{
			throw tooManyAttemptsExceptionSupplier.get();
		}

		if (!confirmationCode.getCode().equals(userProvidedConfirmationCode))
		{
			confirmationCode.incrementAttempts();
			throw invalidConfirmationCodeExceptionSupplier.apply(remainingAttempts - 1);
		}
	}
}
