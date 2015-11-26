/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.User;

@Service
@Transactional
public class BuddyService
{
	@Autowired
	private UserService userService;

	@Autowired
	EmailService emailService;

	@Autowired
	YonaProperties properties;

	public BuddyDTO getBuddy(UUID buddyID)
	{
		return BuddyDTO.createInstance(getEntityByID(buddyID));
	}

	public Set<BuddyDTO> getBuddiesOfUser(UUID forUserID)
	{
		UserDTO user = userService.getPrivateUser(forUserID);
		return getBuddies(user.getPrivateData().getBuddyIDs());
	}

	public BuddyDTO addBuddyToRequestingUser(UUID idOfRequestingUser, BuddyDTO buddy,
			BiFunction<UUID, String, String> inviteURLGetter)
	{
		UserDTO requestingUser = userService.getPrivateUser(idOfRequestingUser);
		User buddyUserEntity = getBuddyUser(buddy);
		BuddyDTO newBuddyEntity;
		if (buddyUserEntity == null)
		{
			newBuddyEntity = handleBuddyRequestForNewUser(requestingUser, buddy, inviteURLGetter);
		}
		else
		{
			newBuddyEntity = handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);
		}
		return newBuddyEntity;
	}

	public BuddyDTO addBuddyToAcceptingUser(UUID buddyUserID, String buddyNickName, UUID buddyLoginID)
	{
		Buddy buddy = Buddy.createInstance(buddyUserID, buddyNickName);
		buddy.setReceivingStatus(BuddyAnonymized.Status.ACCEPTED);
		buddy.setLoginID(buddyLoginID);

		return BuddyDTO.createInstance(Buddy.getRepository().save(buddy));
	}

	public void removeBuddyAfterConnectRejection(UUID idOfRequestingUser, UUID buddyID)
	{
		User user = User.getRepository().findOne(idOfRequestingUser);
		Buddy buddy = getEntityByID(buddyID);

		user.removeBuddy(buddy);
	}

	private BuddyDTO handleBuddyRequestForNewUser(UserDTO requestingUser, BuddyDTO buddy,
			BiFunction<UUID, String, String> inviteURLGetter)
	{
		UserDTO buddyUser = buddy.getUser();

		String tempPassword = userService.generateTempPassword();
		User buddyUserEntity = userService.addUserCreatedOnBuddyRequest(buddyUser, tempPassword);
		BuddyDTO savedBuddy = handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);

		String inviteURL = inviteURLGetter.apply(buddyUserEntity.getID(), tempPassword);
		if (properties.getIsRunningInTestMode())
		{
			savedBuddy.setUserCreatedInviteURL(inviteURL);
		}
		else
		{
			sendInvitationMessage(buddyUserEntity, buddy, inviteURL);
		}
		return savedBuddy;
	}

	private void sendInvitationMessage(User buddyUserEntity, BuddyDTO buddy, String inviteURL)
	{
		emailService.sendEmail(buddy.getUser().getEmailAddress(), "Become my buddy for Yona!",
				"Install the app! Open this URL with the app! " + inviteURL);
	}

	private BuddyDTO handleBuddyRequestForExistingUser(UserDTO requestingUser, BuddyDTO buddy, User buddyUserEntity)
	{
		buddy.getUser().setUserID(buddyUserEntity.getID());
		Buddy buddyEntity = buddy.createBuddyEntity();
		buddyEntity.setSendingStatus(BuddyAnonymized.Status.REQUESTED);
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		BuddyDTO savedBuddy = BuddyDTO.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy); // TODO: how can we do this without using the user password?

		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageDestination.send(BuddyConnectRequestMessage.createInstance(requestingUser.getID(),
				requestingUser.getPrivateData().getVpnProfile().getLoginID(), requestingUser.getPrivateData().getGoals(),
				requestingUser.getPrivateData().getNickName(), buddy.getMessage(), savedBuddyEntity.getID()));
		MessageDestination.getRepository().save(messageDestination);

		return savedBuddy;
	}

	private Buddy getEntityByID(UUID id)
	{
		Buddy entity = Buddy.getRepository().findOne(id);
		if (entity == null)
		{
			throw BuddyNotFoundException.notFound(id);
		}
		return entity;
	}

	private User getBuddyUser(BuddyDTO buddy)
	{
		try
		{
			return UserService.findUserByMobileNumber(buddy.getUser().getMobileNumber());
		}
		catch (UserServiceException e)
		{
			return null;
		}
	}

	public void updateBuddyWithSecretUserInfo(UUID buddyID, UUID loginID, String nickname)
	{
		Buddy buddy = Buddy.getRepository().findOne(buddyID);
		buddy.setLoginID(loginID);
		buddy.setNickName(nickname);
	}

	public Set<BuddyDTO> getBuddies(Set<UUID> buddyIDs)
	{
		return buddyIDs.stream().map(id -> getBuddy(id)).collect(Collectors.toSet());
	}
}
