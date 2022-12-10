/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@Service
class UserDeleteService
{
	private static final Logger logger = LoggerFactory.getLogger(UserDeleteService.class);

	@Autowired(required = false)
	private UserRepository userRepository;

	@Autowired(required = false)
	private MessageSourceRepository messageSourceRepository;

	@Autowired(required = false)
	@Lazy
	private BuddyService buddyService;

	@Autowired(required = false)
	@Lazy
	private DeviceService deviceService;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	@Lazy
	private MessageService messageService;

	@Autowired(required = false)
	@Lazy
	private GoalService goalService;

	@Transactional
	Void deleteUser(User userEntity, Optional<String> message)
	{
		deleteBuddyInfo(userEntity, message);

		UUID userAnonymizedId = userEntity.getUserAnonymizedId();
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId)
				.orElseThrow(() -> InvalidDataException.userAnonymizedIdNotFound(userAnonymizedId));
		deleteGoals(userAnonymizedEntity);
		User updatedUserEntity = deleteMessageSources(userEntity, userAnonymizedEntity);

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

	private void deleteGoals(UserAnonymized userAnonymizedEntity)
	{
		Set<Goal> goals = new HashSet<>(userAnonymizedEntity.getGoals()); // Copy to prevent ConcurrentModificationException
		goals.forEach(g -> goalService.deleteGoal(userAnonymizedEntity, g));
	}

	private User deleteMessageSources(User userEntity, UserAnonymized userAnonymizedEntity)
	{
		MessageSource namedMessageSource = messageService.getMessageSource(userEntity.getNamedMessageSourceId());
		MessageSource anonymousMessageSource = messageService.getMessageSource(userEntity.getAnonymousMessageSourceId());
		userAnonymizedEntity.clearAnonymousDestination();
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		userEntity.clearNamedMessageDestination();
		User updatedUserEntity = userRepository.saveAndFlush(userEntity);

		messageSourceRepository.delete(anonymousMessageSource);
		messageSourceRepository.delete(namedMessageSource);
		messageSourceRepository.flush();
		return updatedUserEntity;
	}

	private void handleBuddyUsersRemovedWhileOffline(User user)
	{
		Set<Buddy> buddiesOfRemovedUsers = user.getBuddiesRelatedToRemovedUsers();
		buddiesOfRemovedUsers.stream().forEach(b -> buddyService.removeBuddyInfoForRemovedUser(user, b));
	}
}