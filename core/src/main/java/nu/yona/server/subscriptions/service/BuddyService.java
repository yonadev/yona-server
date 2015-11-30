/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.email.EmailService;
import nu.yona.server.exceptions.EmailException;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyDisconnectMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;

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

	@Transactional
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

	@Transactional
	public BuddyDTO addBuddyToAcceptingUser(UserDTO acceptingUser, UUID buddyUserID, String buddyNickName, UUID buddyVPNLoginID,
			boolean isRequestingSending, boolean isRequestingReceiving)
	{
		Buddy buddy = Buddy.createInstance(buddyUserID, buddyNickName,
				isRequestingSending ? Status.ACCEPTED : Status.NOT_REQUESTED,
				isRequestingReceiving ? Status.ACCEPTED : Status.NOT_REQUESTED);
		buddy.setVPNLoginID(buddyVPNLoginID);
		BuddyDTO buddyDTO = BuddyDTO.createInstance(Buddy.getRepository().save(buddy));
		userService.addBuddy(acceptingUser, buddyDTO);
		return buddyDTO;
	}

	@Transactional
	public void removeBuddyAfterConnectRejection(UUID idOfRequestingUser, UUID buddyID)
	{
		User user = User.getRepository().findOne(idOfRequestingUser);
		Buddy buddy = getEntityByID(buddyID);

		user.removeBuddy(buddy);
		User.getRepository().save(user);
	}

	@Transactional
	public void removeBuddy(UUID idOfRequestingUser, UUID buddyID, Optional<String> message)
	{
		User user = User.getRepository().findOne(idOfRequestingUser);
		Buddy buddy = getEntityByID(buddyID);

		removeBuddyInfoFromBuddy(user, buddy, message, DropBuddyReason.USER_REMOVED_BUDDY);

		user.removeBuddy(buddy);
		User.getRepository().save(user);
	}

	@Transactional
	void removeBuddyInfoFromBuddy(User requestingUser, Buddy requestingUserBuddy, Optional<String> message,
			DropBuddyReason reason)
	{
		if (requestingUserBuddy.getUser() == null)
		{
			// buddy account was removed in the meantime; nothing to do
			return;
		}

		if (requestingUserBuddy.getSendingStatus() == Status.ACCEPTED
				|| requestingUserBuddy.getReceivingStatus() == Status.ACCEPTED)
		{
			removeNamedMessagesFromUser(requestingUserBuddy, requestingUser.getVPNLoginID());

			UserAnonymized userAnonymized = UserAnonymized.getRepository().findOne(requestingUserBuddy.getVPNLoginID());
			disconnectBuddyFromUser(userAnonymized, requestingUser.getVPNLoginID());
			removeAnonymousMessagesFromUser(userAnonymized, requestingUser.getVPNLoginID());
			sendDropBuddyMessage(requestingUser, requestingUserBuddy, message, reason);
		}
		else
		{
			// remove sent buddy request message
			removeNamedMessagesFromUser(requestingUserBuddy, requestingUser.getVPNLoginID());
		}
	}

	@Transactional
	public void removeBuddyAfterBuddyRemovedConnection(UUID idOfRequestingUser, UUID relatedUserID)
	{
		User user = User.getRepository().findOne(idOfRequestingUser);
		user.removeBuddiesFromUser(relatedUserID);
		User.getRepository().save(user);
	}

	private void sendDropBuddyMessage(User requestingUser, Buddy requestingUserBuddy, Optional<String> message,
			DropBuddyReason reason)
	{
		MessageDestination messageDestination = requestingUserBuddy.getUser().getNamedMessageDestination();
		messageDestination.send(BuddyDisconnectMessage.createInstance(requestingUser.getID(), requestingUser.getVPNLoginID(),
				requestingUser.getNickName(), getDropBuddyMessage(reason, message), reason));
		MessageDestination.getRepository().save(messageDestination);
	}

	private void disconnectBuddyFromUser(UserAnonymized userAnonymized, UUID requestingUserVPNLoginID)
	{
		BuddyAnonymized buddyAnonymizedFromUser = userAnonymized.getBuddyAnonymizedFromUser(requestingUserVPNLoginID);
		buddyAnonymizedFromUser.setDisconnected();
		BuddyAnonymized.getRepository().save(buddyAnonymizedFromUser);
	}

	private void removeNamedMessagesFromUser(Buddy buddy, UUID requestingUserVPNLoginID)
	{
		MessageDestination namedMessageDestination = buddy.getUser().getNamedMessageDestination();
		namedMessageDestination.removeMessagesFromUser(requestingUserVPNLoginID);
		MessageDestination.getRepository().save(namedMessageDestination);
	}

	private void removeAnonymousMessagesFromUser(UserAnonymized userAnonymized, UUID requestingUserVPNLoginID)
	{
		MessageDestination anonymousMessageDestination = userAnonymized.getAnonymousDestination();
		anonymousMessageDestination.removeMessagesFromUser(requestingUserVPNLoginID);
		MessageDestination.getRepository().save(anonymousMessageDestination);
	}

	private String getDropBuddyMessage(DropBuddyReason reason, Optional<String> message)
	{
		if (message.isPresent())
		{
			return message.get();
		}

		switch (reason)
		{
			case USER_ACCOUNT_DELETED:
				return "User account was deleted.";
			case USER_REMOVED_BUDDY:
				return "User removed you as a buddy.";
			default:
				throw new NotImplementedException();
		}
	}

	private BuddyDTO handleBuddyRequestForNewUser(UserDTO requestingUser, BuddyDTO buddy,
			BiFunction<UUID, String, String> inviteURLGetter)
	{
		UserDTO buddyUser = buddy.getUser();

		String tempPassword = userService.generatePassword();
		User buddyUserEntity = userService.addUserCreatedOnBuddyRequest(buddyUser, tempPassword);
		BuddyDTO savedBuddy = handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);

		String inviteURL = inviteURLGetter.apply(buddyUserEntity.getID(), tempPassword);
		if (!properties.getEmail().isEnabled())
		{
			savedBuddy.setUserCreatedInviteURL(inviteURL);
		}
		sendInvitationMessage(requestingUser, buddyUserEntity, buddy, inviteURL);
		if (!properties.getSms().isEnabled())
		{
			savedBuddy.getUser().setConfirmationCode(buddyUserEntity.getConfirmationCode());
		}

		return savedBuddy;
	}

	public enum DropBuddyReason
	{
		USER_ACCOUNT_DELETED, USER_REMOVED_BUDDY
	}

	private void sendInvitationMessage(UserDTO requestingUser, User buddyUserEntity, BuddyDTO buddy, String inviteURL)
	{
		try
		{
			String subjectTemplateName = "buddy-invitation-subject";
			String bodyTemplateName = "buddy-invitation-body";
			String requestingUserName = StringUtils
					.join(new Object[] { requestingUser.getFirstName(), requestingUser.getLastName() }, " ");
			String buddyName = StringUtils.join(new Object[] { buddy.getUser().getFirstName(), buddy.getUser().getLastName() },
					" ");
			InternetAddress buddyAddress = new InternetAddress(buddy.getUser().getEmailAddress(), buddyName);
			Map<String, Object> templateParams = new HashMap<String, Object>();
			templateParams.put("inviteURL", inviteURL);
			templateParams.put("requestingUserName", requestingUserName);
			templateParams.put("buddyName", buddyName);
			emailService.sendEmail(requestingUserName, buddyAddress, subjectTemplateName, bodyTemplateName, templateParams);
		}
		catch (UnsupportedEncodingException e)
		{
			throw EmailException.emailSendingFailed(e);
		}
	}

	private BuddyDTO handleBuddyRequestForExistingUser(UserDTO requestingUser, BuddyDTO buddy, User buddyUserEntity)
	{
		buddy.getUser().setUserID(buddyUserEntity.getID());
		if (buddy.getSendingStatus() != Status.REQUESTED || buddy.getReceivingStatus() != Status.REQUESTED)
		{
			throw new IllegalArgumentException("Only two-way buddies allowed for now");
		}
		Buddy buddyEntity = buddy.createBuddyEntity();
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		BuddyDTO savedBuddy = BuddyDTO.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy);

		boolean isRequestingSending = buddy.getReceivingStatus() == Status.REQUESTED;
		boolean isRequestingReceiving = buddy.getSendingStatus() == Status.REQUESTED;
		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageDestination.send(BuddyConnectRequestMessage.createInstance(requestingUser.getID(),
				requestingUser.getPrivateData().getVpnProfile().getVPNLoginID(), requestingUser.getPrivateData().getGoals(),
				requestingUser.getPrivateData().getNickName(), buddy.getMessage(), savedBuddyEntity.getID(), isRequestingSending,
				isRequestingReceiving));
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

	public void setBuddyAcceptedWithSecretUserInfo(UUID buddyID, UUID vpnLoginID, String nickname)
	{
		Buddy buddy = Buddy.getRepository().findOne(buddyID);
		if (buddy.getSendingStatus() == Status.REQUESTED)
		{
			buddy.setSendingStatus(Status.ACCEPTED);
		}
		if (buddy.getReceivingStatus() == Status.REQUESTED)
		{
			buddy.setReceivingStatus(Status.ACCEPTED);
		}
		buddy.setVPNLoginID(vpnLoginID);
		buddy.setNickName(nickname);
	}

	public Set<BuddyDTO> getBuddies(Set<UUID> buddyIDs)
	{
		return buddyIDs.stream().map(id -> getBuddy(id)).collect(Collectors.toSet());
	}
}
