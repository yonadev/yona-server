/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.BuddyDisconnectMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Service
@Transactional
public class BuddyService
{
	private static final Logger logger = LoggerFactory.getLogger(BuddyService.class);

	@Autowired
	private UserService userService;

	@Autowired
	private MessageService messageService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private SmsService smsService;

	@Autowired
	private Translator translator;

	@Autowired
	private YonaProperties properties;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	public enum DropBuddyReason
	{
		USER_ACCOUNT_DELETED, USER_REMOVED_BUDDY
	}

	public BuddyDTO getBuddy(UUID buddyID)
	{
		Buddy buddyEntity = getEntityByID(buddyID);
		BuddyDTO result = BuddyDTO.createInstance(buddyEntity);
		if (canIncludePrivateData(buddyEntity))
		{
			UUID buddyUserAnonymizedID = getUserAnonymizedIDForBuddy(buddyEntity, "buddy relationship is established");
			result.setGoals(userAnonymizedService.getUserAnonymized(buddyUserAnonymizedID).getGoals().stream()
					.collect(Collectors.toSet()));
		}
		return result;
	}

	static boolean canIncludePrivateData(Buddy buddyEntity)
	{
		return (buddyEntity.getReceivingStatus() == Status.ACCEPTED) || (buddyEntity.getSendingStatus() == Status.ACCEPTED);
	}

	public Set<BuddyDTO> getBuddiesOfUser(UUID forUserID)
	{
		UserDTO user = userService.getPrivateUser(forUserID);
		return getBuddies(user.getPrivateData().getBuddyIDs());
	}

	public Set<BuddyDTO> getBuddiesOfUserThatAcceptedSending(UUID forUserID)
	{
		return getBuddiesOfUser(forUserID).stream().filter(b -> b.getSendingStatus() == Status.ACCEPTED)
				.collect(Collectors.toSet());
	}

	@Transactional
	public BuddyDTO addBuddyToRequestingUser(UUID idOfRequestingUser, BuddyDTO buddy,
			BiFunction<UUID, String, String> inviteURLGetter)
	{
		UserDTO requestingUser = userService.getPrivateUser(idOfRequestingUser);
		requestingUser.assertMobileNumberConfirmed();

		User buddyUserEntity = getBuddyUser(buddy);
		BuddyDTO savedBuddy;
		if (buddyUserEntity == null)
		{
			savedBuddy = handleBuddyRequestForNewUser(requestingUser, buddy, inviteURLGetter);
		}
		else
		{
			savedBuddy = handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);
		}

		logger.info(
				"User with mobile number '{}' and ID '{}' sent buddy connect message to {} user with mobile number '{}' and ID '{}' as buddy",
				requestingUser.getMobileNumber(), requestingUser.getID(), (buddyUserEntity == null) ? "new" : "existing",
				buddy.getUser().getMobileNumber(), buddy.getUser().getID());

		return savedBuddy;
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
		Buddy buddy = Buddy.getRepository().findOne(buddyID);

		if (buddy != null)
		{
			removeBuddy(user, buddy);
		}
		// else: buddy already removed, probably in response to removing the user
	}

	private void removeBuddy(User user, Buddy buddy)
	{
		user.removeBuddy(buddy);
		User.getRepository().save(user);
		Buddy.getRepository().delete(buddy);

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

		User buddyUser = buddy.getUser();
		if (buddyUser == null)
		{
			logger.info("User with mobile number '{}' and ID '{}' removed buddy whose account is already removed", user.getID(),
					user.getMobileNumber());
		}
		else
		{
			logger.info("User with mobile number '{}' and ID '{}' removed buddy with mobile number '{}' and ID '{}' as buddy",
					user.getID(), user.getMobileNumber(), buddyUser.getMobileNumber(), buddyUser.getID());
		}
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
			UUID buddyUserAnonymizedID = getUserAnonymizedIDForBuddy(requestingUserBuddy, "buddy relationship is established");
			UserAnonymizedDTO buddyUserAnonymized = userAnonymizedService.getUserAnonymized(buddyUserAnonymizedID);
			disconnectBuddy(buddyUserAnonymized, requestingUser.getUserAnonymizedID());
			removeAnonymousMessagesSentByUser(buddyUserAnonymized, requestingUser.getUserAnonymizedID());
			sendDropBuddyMessage(requestingUser, requestingUserBuddy, message, reason);
		}
	}

	@Transactional
	void removeBuddyInfoForRemovedUser(User user, Buddy buddy)
	{
		if (buddy.getSendingStatus() == Status.ACCEPTED || buddy.getReceivingStatus() == Status.ACCEPTED)
		{
			// Buddy request was accepted
			removeMessagesSentByBuddy(user, buddy);

			// Send message to "self", to notify the user about the removed buddy user
			UUID buddyUserAnonymizedID = getUserAnonymizedIDForBuddy(buddy, "buddy relationship is established");
			sendDropBuddyMessage(null, buddyUserAnonymizedID, buddy.getNickname(), Optional.empty(),
					DropBuddyReason.USER_ACCOUNT_DELETED, user.getNamedMessageDestination());
		}
		else if (buddy.getSendingStatus() != Status.REJECTED && buddy.getReceivingStatus() != Status.REJECTED)
		{
			// Buddy request was not accepted or rejected yet
			// Send message to "self", as if the requested user declined the buddy request
			UUID buddyUserAnonymizedID = buddy.getUserAnonymizedID().orElse(null); //
			sendBuddyConnectResponseMessage(null, buddyUserAnonymizedID, buddy.getNickname(), user.getUserAnonymizedID(),
					buddy.getID(), Status.REJECTED, getDropBuddyMessage(DropBuddyReason.USER_ACCOUNT_DELETED, Optional.empty()));
		}
		removeBuddy(user, buddy);
	}

	private UUID getUserAnonymizedIDForBuddy(Buddy buddy, String state)
	{
		UUID buddyUserAnonymizedID = buddy.getUserAnonymizedID()
				.orElseThrow(() -> new IllegalStateException("Should have user anonymized ID when " + state));
		return buddyUserAnonymizedID;
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
		Optional<UUID> userAnonymizedID = buddy.getUserAnonymizedID();
		if (!userAnonymizedID.isPresent())
		{
			return;
		}
		removeNamedMessagesSentByUser(user, userAnonymizedID.get());
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(user.getUserAnonymizedID());
		removeAnonymousMessagesSentByUser(userAnonymized, userAnonymizedID.get());
	}

	private void sendDropBuddyMessage(User requestingUser, Buddy requestingUserBuddy, Optional<String> message,
			DropBuddyReason reason)
	{
		sendDropBuddyMessage(requestingUser.getID(), requestingUser.getUserAnonymizedID(), requestingUser.getNickname(), message,
				reason, requestingUserBuddy.getUser().getNamedMessageDestination());
	}

	private void sendDropBuddyMessage(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			Optional<String> message, DropBuddyReason reason, MessageDestination messageDestination)
	{
		MessageDestinationDTO messageDestinationDTO = MessageDestinationDTO.createInstance(messageDestination);
		messageService.sendMessage(BuddyDisconnectMessage.createInstance(senderUserID, senderUserAnonymizedID, senderNickname,
				getDropBuddyMessage(reason, message), reason), messageDestinationDTO);
	}

	void sendBuddyConnectResponseMessage(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			UUID receiverUserAnonymizedID, UUID buddyID, Status status, String responseMessage)
	{
		MessageDestinationDTO messageDestination = userAnonymizedService.getUserAnonymized(receiverUserAnonymizedID)
				.getAnonymousDestination();
		assert messageDestination != null;
		messageService.sendMessage(BuddyConnectResponseMessage.createInstance(senderUserID, senderUserAnonymizedID,
				senderNickname, responseMessage, buddyID, status), messageDestination);
	}

	private void disconnectBuddy(UserAnonymizedDTO userAnonymized, UUID userAnonymizedID)
	{
		Optional<BuddyAnonymized> buddyAnonymized = userAnonymized.getBuddyAnonymized(userAnonymizedID);
		buddyAnonymized.ifPresent(ba -> {
			ba.setDisconnected();
			BuddyAnonymized.getRepository().save(ba);
		});
		// Else: user who requested buddy relationship didn't process the accept message yet
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
			String buddyEmailAddress = buddy.getUser().getEmailAddress();
			String message = buddy.getMessage();
			String buddyMobileNumber = buddy.getUser().getMobileNumber();
			Map<String, Object> templateParams = new HashMap<String, Object>();
			templateParams.put("inviteURL", inviteURL);
			templateParams.put("requestingUserName", requestingUserName);
			templateParams.put("requestingUserMobileNumber", requestingUserMobileNumber);
			templateParams.put("requestingUserNickname", requestingUserNickname);
			templateParams.put("buddyName", buddyName);
			templateParams.put("message", message);
			templateParams.put("emailAddress", buddyEmailAddress);
			emailService.sendEmail(requestingUserName, new InternetAddress(buddyEmailAddress, buddyName), subjectTemplateName,
					bodyTemplateName, templateParams);
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

		String tempPassword = getTempPassword();
		User buddyUserEntity = userService.addUserCreatedOnBuddyRequest(buddyUser, tempPassword);
		BuddyDTO savedBuddy = handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);

		String inviteURL = inviteURLGetter.apply(buddyUserEntity.getID(), tempPassword);
		sendInvitationMessage(requestingUser, buddyUserEntity, buddy, inviteURL);

		return savedBuddy;
	}

	private String getTempPassword()
	{
		return (properties.getEmail().isEnabled()) ? userService.generatePassword() : "abcd";
	}

	private BuddyDTO handleBuddyRequestForExistingUser(UserDTO requestingUser, BuddyDTO buddy, User buddyUserEntity)
	{
		buddy.getUser().setUserID(buddyUserEntity.getID());
		if (buddy.getSendingStatus() != Status.REQUESTED || buddy.getReceivingStatus() != Status.REQUESTED)
		{
			throw BuddyServiceException.onlyTwoWayBuddiesAllowed();
		}
		Buddy buddyEntity = buddy.createBuddyEntity(translator);
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		BuddyDTO savedBuddy = BuddyDTO.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy);

		boolean isRequestingSending = buddy.getReceivingStatus() == Status.REQUESTED;
		boolean isRequestingReceiving = buddy.getSendingStatus() == Status.REQUESTED;
		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageService.sendMessage(
				BuddyConnectRequestMessage.createInstance(requestingUser.getID(),
						requestingUser.getPrivateData().getUserAnonymizedID(), requestingUser.getPrivateData().getNickname(),
						buddy.getMessage(), savedBuddyEntity.getID(), isRequestingSending, isRequestingReceiving),
				MessageDestinationDTO.createInstance(messageDestination));

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
