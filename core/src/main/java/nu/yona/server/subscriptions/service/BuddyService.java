/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.email.EmailService;
import nu.yona.server.exceptions.EmailException;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDestinationDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.BuddyDisconnectMessage;
import nu.yona.server.subscriptions.entities.BuddyInfoChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserService.UserPurpose;
import nu.yona.server.util.TransactionHelper;

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

	@Autowired
	private BuddyConnectResponseMessageDto.Manager connectResponseMessageHandler;

	@Autowired
	private TransactionHelper transactionHelper;

	public enum DropBuddyReason
	{
		USER_ACCOUNT_DELETED, USER_REMOVED_BUDDY
	}

	public BuddyDto getBuddy(UUID buddyId)
	{
		Buddy buddyEntity = getEntityById(buddyId);
		BuddyDto result = BuddyDto.createInstance(buddyEntity);
		if (canIncludePrivateData(buddyEntity))
		{
			UUID buddyUserAnonymizedId = getUserAnonymizedIdForBuddy(buddyEntity);
			result.setGoals(userAnonymizedService.getUserAnonymized(buddyUserAnonymizedId).getGoals().stream()
					.collect(Collectors.toSet()));
		}
		return result;
	}

	static boolean canIncludePrivateData(Buddy buddyEntity)
	{
		return (buddyEntity.getReceivingStatus() == Status.ACCEPTED) || (buddyEntity.getSendingStatus() == Status.ACCEPTED);
	}

	public Set<BuddyDto> getBuddiesOfUser(UUID forUserId)
	{
		UserDto user = userService.getPrivateUser(forUserId);
		return getBuddies(user.getPrivateData().getBuddyIds());
	}

	public Set<BuddyDto> getBuddiesOfUserThatAcceptedSending(UUID forUserId)
	{
		return getBuddiesOfUser(forUserId).stream().filter(b -> b.getSendingStatus() == Status.ACCEPTED)
				.collect(Collectors.toSet());
	}

	@Transactional(propagation = Propagation.NEVER) // It explicitly creates transactions when needed.
	public BuddyDto addBuddyToRequestingUser(UUID idOfRequestingUser, BuddyDto buddy,
			BiFunction<UUID, String, String> inviteUrlGetter)
	{
		UserDto requestingUser = userService.getPrivateUser(idOfRequestingUser);
		assertMobileNumberOfRequestingUserConfirmed(idOfRequestingUser);
		assertValidBuddy(requestingUser, buddy);

		boolean buddyUserExists = buddyUserExists(buddy);
		if (!buddyUserExists)
		{
			createAndInviteBuddyUser(idOfRequestingUser, buddy, inviteUrlGetter);
		}
		BuddyDto savedBuddy = transactionHelper
				.executeInNewTransaction(() -> handleBuddyRequestForExistingUser(idOfRequestingUser, buddy));

		logger.info(
				"User with mobile number '{}' and ID '{}' sent buddy connect message to {} user with mobile number '{}' and ID '{}' as buddy",
				requestingUser.getMobileNumber(), requestingUser.getId(), (buddyUserExists) ? "new" : "existing",
				buddy.getUser().getMobileNumber(), buddy.getUser().getId());

		return savedBuddy;
	}

	private void createAndInviteBuddyUser(UUID idOfRequestingUser, BuddyDto buddy,
			BiFunction<UUID, String, String> inviteUrlGetter)
	{
		String tempPassword = getTempPassword();
		// To ensure the data of the new user is encrypted with the temp password, the user is created in a separate transaction
		// and crypto session.
		UUID savedUserId;
		try (CryptoSession cryptoSession = CryptoSession.start(tempPassword))
		{
			savedUserId = transactionHelper
					.executeInNewTransaction(() -> userService.addUserCreatedOnBuddyRequest(buddy.getUser()).getId());
		}

		String inviteUrl = inviteUrlGetter.apply(savedUserId, tempPassword);
		UserDto requestingUser = userService.getPrivateUser(idOfRequestingUser);
		sendInvitationMessage(requestingUser, buddy, inviteUrl);
	}

	private void assertMobileNumberOfRequestingUserConfirmed(UUID idOfRequestingUser)
	{
		UserDto requestingUser = userService.getPrivateUser(idOfRequestingUser);
		requestingUser.assertMobileNumberConfirmed();
	}

	private void assertValidBuddy(UserDto requestingUser, BuddyDto buddy)
	{
		userService.assertValidUserFields(buddy.getUser(), UserPurpose.BUDDY);
		if (buddy.getSendingStatus() != Status.REQUESTED || buddy.getReceivingStatus() != Status.REQUESTED)
		{
			throw BuddyServiceException.onlyTwoWayBuddiesAllowed();
		}
		String buddyMobileNumber = buddy.getUser().getMobileNumber();
		if (requestingUser.getMobileNumber().equals(buddyMobileNumber))
		{
			throw BuddyServiceException.cannotInviteSelf();
		}
		if (getBuddies(requestingUser.getPrivateData().getBuddyIds()).stream().map(b -> b.getUser().getMobileNumber())
				.anyMatch(m -> m.equals(buddyMobileNumber)))
		{
			throw BuddyServiceException.cannotInviteExistingBuddy();
		}

	}

	@Transactional
	public BuddyDto addBuddyToAcceptingUser(UserDto acceptingUser, UUID buddyUserId, String buddyNickName,
			UUID buddyUserAnonymizedId, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		if (acceptingUser == null)
		{
			throw BuddyServiceException.acceptingUserIsNull();
		}

		acceptingUser.assertMobileNumberConfirmed();
		Buddy buddy = Buddy.createInstance(buddyUserId, buddyNickName,
				isRequestingSending ? Status.ACCEPTED : Status.NOT_REQUESTED,
				isRequestingReceiving ? Status.ACCEPTED : Status.NOT_REQUESTED);
		buddy.setUserAnonymizedId(buddyUserAnonymizedId);
		BuddyDto buddyDto = BuddyDto.createInstance(Buddy.getRepository().save(buddy));
		userService.addBuddy(acceptingUser, buddyDto);
		return buddyDto;
	}

	@Transactional
	public void removeBuddyAfterConnectRejection(UUID idOfRequestingUser, UUID buddyId)
	{
		User user = userService.getValidatedUserbyId(idOfRequestingUser);
		Buddy buddy = Buddy.getRepository().findOne(buddyId);

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
		userAnonymizedService.updateUserAnonymized(user.getUserAnonymizedId(), userAnonymizedEntity);
	}

	@Transactional
	public void removeBuddy(UUID idOfRequestingUser, UUID buddyId, Optional<String> message)
	{
		User user = userService.getValidatedUserbyId(idOfRequestingUser);
		Buddy buddy = getEntityById(buddyId);

		if (buddy.getSendingStatus() == Status.REQUESTED || buddy.getReceivingStatus() == Status.REQUESTED)
		{
			// The buddy might already have responded while the response wasn't processed yet
			processPossiblePendingBuddyResponseMessage(user, buddy);
		}

		removeMessagesSentByBuddy(user, buddy);
		removeBuddyInfoForBuddy(user, buddy, message, DropBuddyReason.USER_REMOVED_BUDDY);

		removeBuddy(user, buddy);

		User buddyUser = buddy.getUser();
		if (buddyUser == null)
		{
			logger.info("User with mobile number '{}' and ID '{}' removed buddy whose account is already removed",
					user.getMobileNumber(), user.getId());
		}
		else
		{
			logger.info("User with mobile number '{}' and ID '{}' removed buddy with mobile number '{}' and ID '{}' as buddy",
					user.getMobileNumber(), user.getId(), buddyUser.getMobileNumber(), buddyUser.getId());
		}
	}

	private void processPossiblePendingBuddyResponseMessage(User userEntity, Buddy buddy)
	{
		int page = 0;
		final int pageSize = 50;
		Page<Message> messagePage;
		boolean messageFound = false;
		UserDto user = userService.createUserDtoWithPrivateData(userEntity);
		do
		{
			messagePage = messageService.getReceivedMessageEntitiesSinceDate(user.getId(), buddy.getLastStatusChangeTime(),
					new PageRequest(page++, pageSize));

			messageFound = processPossiblePendingBuddyResponseMessage(user, buddy, messagePage);
		}
		while (!messageFound && messagePage.getNumberOfElements() == pageSize);
	}

	private boolean processPossiblePendingBuddyResponseMessage(UserDto user, Buddy buddy, Page<Message> messagePage)
	{

		Stream<BuddyConnectResponseMessage> buddyConnectResponseMessages = messagePage.getContent().stream()
				.filter(m -> m instanceof BuddyConnectResponseMessage).map(m -> (BuddyConnectResponseMessage) m);
		Stream<BuddyConnectResponseMessage> messagesFromBuddy = buddyConnectResponseMessages
				.filter(m -> buddy.getUserId().equals(getUserId(m).orElse(null)));
		Optional<BuddyConnectResponseMessage> messageToBeProcessed = messagesFromBuddy.filter(m -> m.isProcessed() == false)
				.findFirst();
		messageToBeProcessed.ifPresent(
				m -> connectResponseMessageHandler.handleAction_Process(user, m, new MessageActionDto(Collections.emptyMap())));
		return messageToBeProcessed.isPresent();
	}

	public void processPossiblePendingBuddyResponseMessages(User userEntity)
	{
		getBuddyEntitiesOfUser(userEntity.getId()).stream()
				.forEach(b -> processPossiblePendingBuddyResponseMessage(userEntity, b));
	}

	private Optional<UUID> getUserId(BuddyConnectResponseMessage message)
	{
		return message.getSenderUser().map(u -> u.getId());
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

		removeNamedMessagesSentByUser(requestingUserBuddy.getUser(), requestingUser.getUserAnonymizedId());
		if (requestingUserBuddy.getSendingStatus() == Status.ACCEPTED
				|| requestingUserBuddy.getReceivingStatus() == Status.ACCEPTED)
		{
			UUID buddyUserAnonymizedId = getUserAnonymizedIdForBuddy(requestingUserBuddy);
			UserAnonymizedDto buddyUserAnonymized = userAnonymizedService.getUserAnonymized(buddyUserAnonymizedId);
			disconnectBuddyIfConnected(buddyUserAnonymized, requestingUser.getUserAnonymizedId());
			removeAnonymousMessagesSentByUser(buddyUserAnonymized, requestingUser.getUserAnonymizedId());
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
			removeMessagesSentByUserToBuddy(user, buddy);

			// Send message to "self", to notify the user about the removed buddy user
			UUID buddyUserAnonymizedId = getUserAnonymizedIdForBuddy(buddy);
			sendDropBuddyMessage(null, buddyUserAnonymizedId, buddy.getNickname(), Optional.empty(),
					DropBuddyReason.USER_ACCOUNT_DELETED, user.getNamedMessageDestination());
		}
		else if (buddy.getSendingStatus() != Status.REJECTED && buddy.getReceivingStatus() != Status.REJECTED)
		{
			// Buddy request was not accepted or rejected yet
			// Send message to "self", as if the requested user declined the buddy request
			UUID buddyUserAnonymizedId = buddy.getUserAnonymizedId().orElse(null); //
			sendBuddyConnectResponseMessage(null, buddyUserAnonymizedId, buddy.getNickname(), user.getUserAnonymizedId(),
					buddy.getId(), Status.REJECTED, getDropBuddyMessage(DropBuddyReason.USER_ACCOUNT_DELETED, Optional.empty()));
		}
		removeBuddy(user, buddy);
	}

	private UUID getUserAnonymizedIdForBuddy(Buddy buddy)
	{
		UUID buddyUserAnonymizedId = buddy.getUserAnonymizedId().orElseThrow(
				() -> new IllegalStateException("Should have user anonymized ID when buddy relationship is established"));
		return buddyUserAnonymizedId;
	}

	@Transactional
	public void removeBuddyAfterBuddyRemovedConnection(UUID idOfRequestingUser, UUID relatedUserId)
	{
		User user = userService.getValidatedUserbyId(idOfRequestingUser);

		user.removeBuddiesFromUser(relatedUserId);
		User.getRepository().save(user);
	}

	public void setBuddyAcceptedWithSecretUserInfo(UUID buddyId, UUID userAnonymizedId, String nickname)
	{
		Buddy buddy = Buddy.getRepository().findOne(buddyId);
		if (buddy == null)
		{
			throw BuddyNotFoundException.notFound(buddyId);
		}

		if (buddy.getSendingStatus() == Status.REQUESTED)
		{
			buddy.setSendingStatus(Status.ACCEPTED);
		}
		if (buddy.getReceivingStatus() == Status.REQUESTED)
		{
			buddy.setReceivingStatus(Status.ACCEPTED);
		}
		buddy.setUserAnonymizedId(userAnonymizedId);
		buddy.setNickName(nickname);
		Buddy.getRepository().save(buddy);
	}

	public Set<BuddyDto> getBuddies(Set<UUID> buddyIds)
	{
		return buddyIds.stream().map(id -> getBuddy(id)).collect(Collectors.toSet());
	}

	private Set<Buddy> getBuddyEntitiesOfUser(UUID forUserId)
	{
		UserDto user = userService.getPrivateUser(forUserId);
		return getBuddyEntities(user.getPrivateData().getBuddyIds());
	}

	private Set<Buddy> getBuddyEntities(Set<UUID> buddyIds)
	{
		return buddyIds.stream().map(id -> getEntityById(id)).collect(Collectors.toSet());
	}

	private void removeMessagesSentByBuddy(User user, Buddy buddy)
	{
		Optional<UUID> buddyUserAnonymizedId = buddy.getUserAnonymizedId();
		if (!buddyUserAnonymizedId.isPresent())
		{
			return;
		}
		removeNamedMessagesSentByUser(user, buddyUserAnonymizedId.get());
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(user.getUserAnonymizedId());
		removeAnonymousMessagesSentByUser(userAnonymized, buddyUserAnonymizedId.get());
	}

	private void removeMessagesSentByUserToBuddy(User user, Buddy buddy)
	{
		UUID buddyUserAnonymizedId = getUserAnonymizedIdForBuddy(buddy);
		UserAnonymizedDto buddyUserAnonymized = userAnonymizedService.getUserAnonymized(buddyUserAnonymizedId);
		removeAnonymousMessagesSentByUser(buddyUserAnonymized, user.getUserAnonymizedId());
		// We are not removing the named messages because we don't have User entity anymore
		// (this method is being called from removeBuddyInfoForRemovedUser) and thus we don't know the named destination.
		// Given that the user and the named destination are both removed, this is not causing any issues.
	}

	private void sendDropBuddyMessage(User requestingUser, Buddy requestingUserBuddy, Optional<String> message,
			DropBuddyReason reason)
	{
		sendDropBuddyMessage(requestingUser.getId(), requestingUser.getUserAnonymizedId(), requestingUser.getNickname(), message,
				reason, requestingUserBuddy.getUser().getNamedMessageDestination());
	}

	private void sendDropBuddyMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			Optional<String> message, DropBuddyReason reason, MessageDestination messageDestination)
	{
		MessageDestinationDto messageDestinationDto = MessageDestinationDto.createInstance(messageDestination);
		messageService.sendMessageAndFlushToDatabase(BuddyDisconnectMessage.createInstance(senderUserId, senderUserAnonymizedId,
				senderNickname, getDropBuddyMessage(reason, message), reason), messageDestinationDto);
	}

	void sendBuddyConnectResponseMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			UUID receiverUserAnonymizedId, UUID buddyId, Status status, String responseMessage)
	{
		MessageDestinationDto messageDestination = userAnonymizedService.getUserAnonymized(receiverUserAnonymizedId)
				.getAnonymousDestination();
		assert messageDestination != null;
		messageService.sendMessageAndFlushToDatabase(BuddyConnectResponseMessage.createInstance(senderUserId,
				senderUserAnonymizedId, senderNickname, responseMessage, buddyId, status), messageDestination);
	}

	private void disconnectBuddyIfConnected(UserAnonymizedDto buddyUserAnonymized, UUID userAnonymizedId)
	{
		Optional<BuddyAnonymized> buddyAnonymized = buddyUserAnonymized.getBuddyAnonymized(userAnonymizedId);
		buddyAnonymized.ifPresent(ba -> {
			ba.setDisconnected();
			BuddyAnonymized.getRepository().save(ba);
			// Notice: last status change time will not be set, as we are not able to reach the Buddy entity from here
			// Buddy will be removed anyway the first time the other user logs in
		});
		// Else: user who requested buddy relationship didn't process the accept message yet
	}

	private void removeNamedMessagesSentByUser(User receivingUser, UUID sentByUserAnonymizedId)
	{
		MessageDestination namedMessageDestination = receivingUser.getNamedMessageDestination();
		messageService.removeMessagesFromUser(MessageDestinationDto.createInstance(namedMessageDestination),
				sentByUserAnonymizedId);
	}

	private void removeAnonymousMessagesSentByUser(UserAnonymizedDto receivingUserAnonymized, UUID sentByUserAnonymizedId)
	{
		MessageDestinationDto anonymousMessageDestination = receivingUserAnonymized.getAnonymousDestination();
		messageService.removeMessagesFromUser(anonymousMessageDestination, sentByUserAnonymizedId);
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

	private void sendInvitationMessage(UserDto requestingUser, BuddyDto buddy, String inviteUrl)
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
			String personalInvitationMessage = buddy.getPersonalInvitationMessage();
			String buddyMobileNumber = buddy.getUser().getMobileNumber();
			Map<String, Object> templateParams = new HashMap<>();
			templateParams.put("inviteUrl", inviteUrl);
			templateParams.put("requestingUserFirstName", requestingUser.getFirstName());
			templateParams.put("requestingUserLastName", requestingUser.getLastName());
			templateParams.put("requestingUserMobileNumber", requestingUserMobileNumber);
			templateParams.put("requestingUserNickname", requestingUserNickname);
			templateParams.put("buddyFirstName", buddy.getUser().getFirstName());
			templateParams.put("buddyLastName", buddy.getUser().getLastName());
			templateParams.put("personalInvitationMessage", personalInvitationMessage);
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

	private String getTempPassword()
	{
		return (properties.getEmail().isEnabled()) ? userService.generatePassword() : "abcd";
	}

	private BuddyDto handleBuddyRequestForExistingUser(UUID idOfRequestingUser, BuddyDto buddy)
	{
		UserDto requestingUser = userService.getPrivateUser(idOfRequestingUser);
		User buddyUserEntity = UserService.findUserByMobileNumber(buddy.getUser().getMobileNumber());
		buddy.getUser().setUserId(buddyUserEntity.getId());
		Buddy buddyEntity = buddy.createBuddyEntity(translator);
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		BuddyDto savedBuddy = BuddyDto.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy);

		boolean isRequestingSending = buddy.getReceivingStatus() == Status.REQUESTED;
		boolean isRequestingReceiving = buddy.getSendingStatus() == Status.REQUESTED;
		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageService.sendMessageAndFlushToDatabase(BuddyConnectRequestMessage.createInstance(requestingUser.getId(),
				requestingUser.getPrivateData().getUserAnonymizedId(), requestingUser.getPrivateData().getNickname(),
				buddy.getPersonalInvitationMessage(), savedBuddyEntity.getId(), isRequestingSending, isRequestingReceiving),
				MessageDestinationDto.createInstance(messageDestination));

		return savedBuddy;
	}

	private Buddy getEntityById(UUID id)
	{
		Buddy entity = Buddy.getRepository().findOne(id);
		if (entity == null)
		{
			throw BuddyNotFoundException.notFound(id);
		}
		return entity;
	}

	private boolean buddyUserExists(BuddyDto buddy)
	{
		try
		{
			if (buddy.getUser() == null)
			{
				throw UserServiceException.missingOrNullUser();
			}
			UserService.findUserByMobileNumber(buddy.getUser().getMobileNumber());
			return true;
		}
		catch (UserServiceException e)
		{
			return false;
		}
	}

	public Optional<BuddyDto> getBuddyOfUserByUserAnonymizedId(UUID forUserId, UUID userAnonymizedId)
	{
		Set<BuddyDto> buddies = getBuddiesOfUser(forUserId);
		for (BuddyDto buddy : buddies)
		{
			if (buddy.getUserAnonymizedId().filter(id -> id.equals(userAnonymizedId)).isPresent())
			{
				return Optional.of(buddy);
			}
		}
		return Optional.empty();
	}

	@Transactional
	void broadcastUserInfoChangeToBuddies(User updatedUserEntity, UserDto originalUser)
	{
		messageService.broadcastMessageToBuddies(UserAnonymizedDto.createInstance(updatedUserEntity.getAnonymized()),
				() -> BuddyInfoChangeMessage.createInstance(updatedUserEntity.getId(), updatedUserEntity.getUserAnonymizedId(),
						originalUser.getPrivateData().getNickname(), getUserInfoChangeMessage(updatedUserEntity, originalUser),
						updatedUserEntity.getNickname()));
	}

	private String getUserInfoChangeMessage(User updatedUserEntity, UserDto originalUser)
	{
		return translator.getLocalizedMessage("message.buddy.user.info.changed");
	}

	@Transactional
	public void updateBuddyUserInfo(UUID idOfRequestingUser, UUID relatedUserAnonymizedId, String buddyNickname)
	{
		User user = userService.getValidatedUserbyId(idOfRequestingUser);

		Buddy buddy = user.getBuddyByUserAnonymizedId(relatedUserAnonymizedId);
		buddy.setNickName(buddyNickname);
		Buddy.getRepository().save(buddy);
	}
}
