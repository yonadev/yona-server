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

import nu.yona.server.Translator;
import nu.yona.server.email.EmailService;
import nu.yona.server.exceptions.EmailException;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
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
	private MessageService messageService;

	@Autowired
	EmailService emailService;

	@Autowired
	SmsService smsService;

	@Autowired
	Translator translator;

	@Autowired
	YonaProperties properties;

	@Autowired
	UserAnonymizedService userAnonymizedService;

	public enum DropBuddyReason
	{
		USER_ACCOUNT_DELETED, USER_REMOVED_BUDDY
	}

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
		requestingUser.assertMobileNumberConfirmed();

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
	public BuddyDTO addBuddyToAcceptingUser(UserDTO acceptingUser, UUID buddyUserID, String buddyNickName,
			UUID buddyUserAnonymizedID, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		if (acceptingUser == null)
		{
			throw BuddyServiceException.acceptingUserIsNull();
		}

		acceptingUser.assertMobileNumberConfirmed();
		Buddy buddy = Buddy.createInstance(buddyUserID, buddyNickName,
				isRequestingSending ? Status.ACCEPTED : Status.NOT_REQUESTED,
				isRequestingReceiving ? Status.ACCEPTED : Status.NOT_REQUESTED);
		buddy.setUserAnonymizedID(buddyUserAnonymizedID);
		BuddyDTO buddyDTO = BuddyDTO.createInstance(Buddy.getRepository().save(buddy));
		userService.addBuddy(acceptingUser, buddyDTO);
		return buddyDTO;
	}

	@Transactional
	public void removeBuddyAfterConnectRejection(UUID idOfRequestingUser, UUID buddyID)
	{
		User user = userService.getValidatedUserbyID(idOfRequestingUser);
		Buddy buddy = getEntityByID(buddyID);

		removeBuddy(user, buddy);
	}

	private void removeBuddy(User user, Buddy buddy)
	{
		user.removeBuddy(buddy);
		User.getRepository().save(user);

		UserAnonymized userAnonymizedEntity = user.getAnonymized();
		userAnonymizedEntity.removeBuddyAnonymized(buddy.getBuddyAnonymized());
		userAnonymizedService.updateUserAnonymized(user.getUserAnonymizedID(), userAnonymizedEntity);
	}

	@Transactional
	public void removeBuddy(UUID idOfRequestingUser, UUID buddyID, Optional<String> message)
	{
		User user = userService.getValidatedUserbyID(idOfRequestingUser);
		Buddy buddy = getEntityByID(buddyID);

		removeMessagesSentByBuddy(user, buddy);
		removeBuddyInfoForBuddy(user, buddy, message, DropBuddyReason.USER_REMOVED_BUDDY);

		removeBuddy(user, buddy);
	}

	@Transactional
	void removeBuddyInfoForBuddy(User requestingUser, Buddy requestingUserBuddy, Optional<String> message, DropBuddyReason reason)
	{
		if (requestingUserBuddy == null)
		{
			throw BuddyServiceException.requestingUserBuddyIsNull();
		}

		if (requestingUserBuddy.getUser() == null)
		{
			// buddy account was removed in the meantime; nothing to do
			return;
		}

		removeNamedMessagesSentByUser(requestingUserBuddy.getUser(), requestingUser.getUserAnonymizedID());
		if (requestingUserBuddy.getSendingStatus() == Status.ACCEPTED
				|| requestingUserBuddy.getReceivingStatus() == Status.ACCEPTED)
		{
			UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(requestingUserBuddy.getUserAnonymizedID());
			disconnectBuddy(userAnonymized, requestingUser.getUserAnonymizedID());
			removeAnonymousMessagesSentByUser(userAnonymized, requestingUser.getUserAnonymizedID());
			sendDropBuddyMessage(requestingUser, requestingUserBuddy, message, reason);
		}
	}

	@Transactional
	public void removeBuddyAfterBuddyRemovedConnection(UUID idOfRequestingUser, UUID relatedUserID)
	{
		User user = userService.getValidatedUserbyID(idOfRequestingUser);

		user.removeBuddiesFromUser(relatedUserID);
		User.getRepository().save(user);
	}

	public void setBuddyAcceptedWithSecretUserInfo(UUID buddyID, UUID userAnonymizedID, String nickname)
	{
		Buddy buddy = Buddy.getRepository().findOne(buddyID);
		if (buddy == null)
		{
			throw BuddyNotFoundException.notFound(buddyID);
		}

		if (buddy.getSendingStatus() == Status.REQUESTED)
		{
			buddy.setSendingStatus(Status.ACCEPTED);
		}
		if (buddy.getReceivingStatus() == Status.REQUESTED)
		{
			buddy.setReceivingStatus(Status.ACCEPTED);
		}
		buddy.setUserAnonymizedID(userAnonymizedID);
		buddy.setNickName(nickname);
	}

	public Set<BuddyDTO> getBuddies(Set<UUID> buddyIDs)
	{
		return buddyIDs.stream().map(id -> getBuddy(id)).collect(Collectors.toSet());
	}

	private void removeMessagesSentByBuddy(User user, Buddy buddy)
	{
		removeNamedMessagesSentByUser(user, buddy.getUserAnonymizedID());
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(user.getUserAnonymizedID());
		removeAnonymousMessagesSentByUser(userAnonymized, buddy.getUserAnonymizedID());
	}

	private void sendDropBuddyMessage(User requestingUser, Buddy requestingUserBuddy, Optional<String> message,
			DropBuddyReason reason)
	{
		MessageDestinationDTO messageDestination = userAnonymizedService
				.getUserAnonymized(requestingUserBuddy.getUserAnonymizedID()).getAnonymousDestination();
		messageService.sendMessage(BuddyDisconnectMessage.createInstance(requestingUser.getID(),
				requestingUser.getUserAnonymizedID(), requestingUser.getNickname(), getDropBuddyMessage(reason, message), reason),
				messageDestination);

	}

	private void disconnectBuddy(UserAnonymizedDTO userAnonymized, UUID userAnonymizedID)
	{
		BuddyAnonymized buddyAnonymized = userAnonymized.getBuddyAnonymized(userAnonymizedID);
		buddyAnonymized.setDisconnected();
		BuddyAnonymized.getRepository().save(buddyAnonymized);
	}

	private void removeNamedMessagesSentByUser(User user, UUID sentByUserAnonymizedID)
	{
		MessageDestination namedMessageDestination = user.getNamedMessageDestination();
		messageService.removeMessagesFromUser(MessageDestinationDTO.createInstance(namedMessageDestination),
				sentByUserAnonymizedID);
	}

	private void removeAnonymousMessagesSentByUser(UserAnonymizedDTO userAnonymized, UUID sentByUserAnonymizedID)
	{
		MessageDestinationDTO anonymousMessageDestination = userAnonymized.getAnonymousDestination();
		messageService.removeMessagesFromUser(anonymousMessageDestination, sentByUserAnonymizedID);
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
				return translator.getLocalizedMessage("message.user.account.deleted");
			case USER_REMOVED_BUDDY:
				return translator.getLocalizedMessage("message.user.removed.buddy");
			default:
				throw new NotImplementedException();
		}
	}

	private void sendInvitationMessage(UserDTO requestingUser, User buddyUserEntity, BuddyDTO buddy, String inviteURL)
	{
		try
		{
			String subjectTemplateName = "buddy-invitation-subject";
			String bodyTemplateName = "buddy-invitation-body";
			String requestingUserName = StringUtils
					.join(new Object[] { requestingUser.getFirstName(), requestingUser.getLastName() }, " ");
			String requestingUserMobileNumber = requestingUser.getMobileNumber();
			String requestingUserNickname = requestingUser.getPrivateData().getNickname();
			String buddyName = StringUtils.join(new Object[] { buddy.getUser().getFirstName(), buddy.getUser().getLastName() },
					" ");
			InternetAddress buddyAddress = new InternetAddress(buddy.getUser().getEmailAddress(), buddyName);
			String message = buddy.getMessage();
			String buddyMobileNumber = buddy.getUser().getMobileNumber();
			Map<String, Object> templateParams = new HashMap<String, Object>();
			templateParams.put("inviteURL", inviteURL);
			templateParams.put("requestingUserName", requestingUserName);
			templateParams.put("requestingUserMobileNumber", requestingUserMobileNumber);
			templateParams.put("requestingUserNickname", requestingUserNickname);
			templateParams.put("buddyName", buddyName);
			templateParams.put("message", message);
			emailService.sendEmail(requestingUserName, buddyAddress, subjectTemplateName, bodyTemplateName, templateParams);
			smsService.send(buddyMobileNumber, SmsService.TemplateName_BuddyInvite, templateParams);
		}
		catch (UnsupportedEncodingException e)
		{
			throw EmailException.emailSendingFailed(e);
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

		return savedBuddy;
	}

	private BuddyDTO handleBuddyRequestForExistingUser(UserDTO requestingUser, BuddyDTO buddy, User buddyUserEntity)
	{
		buddy.getUser().setUserID(buddyUserEntity.getID());
		if (buddy.getSendingStatus() != Status.REQUESTED || buddy.getReceivingStatus() != Status.REQUESTED)
		{
			throw BuddyServiceException.onlyTwoWayBuddiesAllowed();
		}
		Buddy buddyEntity = buddy.createBuddyEntity();
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		BuddyDTO savedBuddy = BuddyDTO.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy);

		boolean isRequestingSending = buddy.getReceivingStatus() == Status.REQUESTED;
		boolean isRequestingReceiving = buddy.getSendingStatus() == Status.REQUESTED;
		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageService.sendMessage(BuddyConnectRequestMessage.createInstance(requestingUser.getID(),
				requestingUser.getPrivateData().getUserAnonymizedID(), requestingUser.getPrivateData().getGoals(),
				requestingUser.getPrivateData().getNickname(), buddy.getMessage(), savedBuddyEntity.getID(), isRequestingSending,
				isRequestingReceiving), MessageDestinationDTO.createInstance(messageDestination));

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
}
