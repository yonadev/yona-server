/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import nu.yona.server.device.entities.BuddyDevice;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.service.DeviceChange;
import nu.yona.server.email.EmailService;
import nu.yona.server.exceptions.EmailException;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDestinationDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.BuddyConnectMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.BuddyDisconnectMessage;
import nu.yona.server.subscriptions.entities.BuddyInfoChangeMessage;
import nu.yona.server.subscriptions.entities.BuddyRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserService.UserPurpose;
import nu.yona.server.util.Require;
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

	@Autowired(required = false)
	private EmailService emailService;

	@Autowired(required = false)
	private SmsService smsService;

	@Autowired
	private Translator translator;

	@Autowired
	private YonaProperties properties;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private BuddyRepository buddyRepository;

	@Autowired(required = false)
	private BuddyAnonymizedRepository buddyAnonymizedRepository;

	@Autowired(required = false)
	private BuddyConnectResponseMessageDto.Manager connectResponseMessageHandler;

	@Autowired
	private TransactionHelper transactionHelper;

	public enum DropBuddyReason
	{
		USER_ACCOUNT_DELETED, USER_REMOVED_BUDDY
	}

	public BuddyDto getBuddy(UUID buddyId)
	{
		return getBuddy(getEntityById(buddyId));
	}

	private BuddyDto getBuddy(Buddy buddyEntity)
	{
		if (canIncludePrivateData(buddyEntity))
		{
			return BuddyDto.createInstance(buddyEntity);
		}
		return BuddyDto.createInstance(buddyEntity);
	}

	static boolean canIncludePrivateData(Buddy buddyEntity)
	{
		return (buddyEntity.getReceivingStatus() == Status.ACCEPTED) || (buddyEntity.getSendingStatus() == Status.ACCEPTED);
	}

	public Set<BuddyDto> getBuddiesOfUser(UUID forUserId)
	{
		UserDto user = userService.getUser(forUserId);
		return user.getOwnPrivateData().getBuddies();
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
		UserDto requestingUser = userService.getUser(idOfRequestingUser);
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
				requestingUser.getMobileNumber(), requestingUser.getId(), (buddyUserExists) ? "existing" : "new",
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
		UserDto requestingUser = userService.getUser(idOfRequestingUser);
		sendInvitationMessage(requestingUser, buddy, inviteUrl);
	}

	private void assertMobileNumberOfRequestingUserConfirmed(UUID idOfRequestingUser)
	{
		UserDto requestingUser = userService.getUser(idOfRequestingUser);
		requestingUser.assertMobileNumberConfirmed();
	}

	private void assertValidBuddy(UserDto requestingUser, BuddyDto buddy)
	{
		userService.assertValidUserFields(buddy.getUser(), UserPurpose.BUDDY);
		Require.that(buddy.getSendingStatus() == Status.REQUESTED && buddy.getReceivingStatus() == Status.REQUESTED,
				BuddyServiceException::onlyTwoWayBuddiesAllowed);
		String buddyMobileNumber = buddy.getUser().getMobileNumber();
		Require.that(!requestingUser.getMobileNumber().equals(buddyMobileNumber), BuddyServiceException::cannotInviteSelf);
		Require.that(requestingUser.getOwnPrivateData().getBuddies().stream().map(b -> b.getUser().getMobileNumber())
				.noneMatch(m -> m.equals(buddyMobileNumber)), BuddyServiceException::cannotInviteExistingBuddy);
	}

	@Transactional
	public BuddyDto addBuddyToAcceptingUser(UserDto acceptingUser, BuddyConnectRequestMessage connectRequestMessageEntity)
	{
		Require.isNonNull(acceptingUser, BuddyServiceException::acceptingUserIsNull);
		Require.isPresent(connectRequestMessageEntity.getSenderUser(),
				() -> UserServiceException.notFoundById(connectRequestMessageEntity.getSenderUserId()));

		acceptingUser.assertMobileNumberConfirmed();
		Buddy buddy = createBuddyEntity(connectRequestMessageEntity);
		BuddyDto buddyDto = BuddyDto.createInstance(buddyRepository.save(buddy));
		userService.addBuddy(acceptingUser, buddyDto);
		return buddyDto;
	}

	private Buddy createBuddyEntity(BuddyConnectRequestMessage connectRequestMessageEntity)
	{
		Optional<User> senderUser = connectRequestMessageEntity.getSenderUser();
		Buddy buddy = Buddy.createInstance(senderUser.get().getId(), connectRequestMessageEntity.determineFirstName(senderUser),
				connectRequestMessageEntity.determineLastName(senderUser), connectRequestMessageEntity.getSenderNickname(),
				connectRequestMessageEntity.getSenderUserPhotoId(),
				connectRequestMessageEntity.requestingSending() ? Status.ACCEPTED : Status.NOT_REQUESTED,
				connectRequestMessageEntity.requestingReceiving() ? Status.ACCEPTED : Status.NOT_REQUESTED);
		buddy.setUserAnonymizedId(connectRequestMessageEntity.getRelatedUserAnonymizedId().get());
		createBuddyDevices(connectRequestMessageEntity).forEach(buddy::addDevice);
		return buddy;
	}

	private Set<BuddyDevice> createBuddyDevices(BuddyConnectMessage connectMessageEntity)
	{
		List<String> deviceNames = connectMessageEntity.getDeviceNames();
		List<UUID> deviceAnonymizedIds = connectMessageEntity.getDeviceAnonymizedIds();
		int numDevices = deviceNames.size();
		assert deviceAnonymizedIds.size() == numDevices;

		Set<BuddyDevice> devices = new HashSet<>();
		for (int i = 0; (i < numDevices); i++)
		{
			devices.add(BuddyDevice.createInstance(deviceNames.get(i), deviceAnonymizedIds.get(i)));
		}
		return devices;
	}

	@Transactional
	public void removeBuddyAfterConnectRejection(UUID idOfRequestingUser, UUID buddyId)
	{
		userService.assertValidatedUser(userService.getUserEntityById(idOfRequestingUser));
		buddyRepository.findById(buddyId).ifPresent(buddy -> removeBuddy(idOfRequestingUser, buddy));
		// else: buddy already removed, probably in response to removing the user
	}

	private void removeBuddy(UUID userId, Buddy buddy)
	{
		userService.updateUser(userId, user -> {
			user.removeBuddy(buddy);
			buddyRepository.delete(buddy);

			UserAnonymized userAnonymizedEntity = user.getAnonymized();
			userAnonymizedEntity.removeBuddyAnonymized(buddy.getBuddyAnonymized());
			userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
		});
	}

	@Transactional
	public void removeBuddy(UUID idOfRequestingUser, UUID buddyId, Optional<String> message)
	{
		User user = userService.getValidatedUserById(idOfRequestingUser);
		Buddy buddy = getEntityById(buddyId);

		if (buddy.getSendingStatus() == Status.REQUESTED || buddy.getReceivingStatus() == Status.REQUESTED)
		{
			// The buddy might already have responded while the response wasn't processed yet
			processPossiblePendingBuddyResponseMessage(user, buddy);
		}

		removeMessagesSentByBuddy(user, buddy);
		removeBuddyInfoForBuddy(user, buddy, message, DropBuddyReason.USER_REMOVED_BUDDY);

		removeBuddy(user.getId(), buddy);

		logBuddyRemoval(user, buddy);
	}

	private void logBuddyRemoval(User user, Buddy buddy)
	{
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
		UserDto user = userService.createUserDto(userEntity);
		do
		{
			messagePage = messageService.getReceivedMessageEntitiesSinceDate(user.getId(), buddy.getLastStatusChangeTime(),
					PageRequest.of(page++, pageSize));

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
		Optional<BuddyConnectResponseMessage> messageToBeProcessed = messagesFromBuddy.filter(m -> !m.isProcessed()).findFirst();
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
		return message.getSenderUser().map(User::getId);
	}

	@Transactional
	void removeBuddyInfoForBuddy(User requestingUser, Buddy requestingUserBuddy, Optional<String> message, DropBuddyReason reason)
	{
		Require.isNonNull(requestingUserBuddy, BuddyServiceException::requestingUserBuddyIsNull);

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
			sendDropBuddyMessage(BuddyInfoParameters.createInstance(buddy, buddyUserAnonymizedId), Optional.empty(),
					DropBuddyReason.USER_ACCOUNT_DELETED, user);
		}
		else if (buddy.getSendingStatus() != Status.REJECTED && buddy.getReceivingStatus() != Status.REJECTED)
		{
			// Buddy request was not accepted or rejected yet
			// Send message to "self", as if the requested user declined the buddy request
			UUID buddyUserAnonymizedId = buddy.getUserAnonymizedId().orElse(null); //
			sendBuddyConnectResponseMessage(BuddyInfoParameters.createInstance(buddy, buddyUserAnonymizedId),
					user.getUserAnonymizedId(), buddy.getId(), user.getDevices(), Status.REJECTED,
					getDropBuddyMessage(DropBuddyReason.USER_ACCOUNT_DELETED, Optional.empty()));
		}
		removeBuddy(user.getId(), buddy);
	}

	private UUID getUserAnonymizedIdForBuddy(Buddy buddy)
	{
		return buddy.getUserAnonymizedId().orElseThrow(
				() -> new IllegalStateException("Should have user anonymized ID when buddy relationship is established"));
	}

	@Transactional
	public void removeBuddyAfterBuddyRemovedConnection(UUID idOfRequestingUser, UUID relatedUserId)
	{
		User user = userService.getValidatedUserById(idOfRequestingUser);
		user.getBuddies().stream().filter(b -> b.getUserId().equals(relatedUserId)).findFirst()
				.ifPresent(b -> removeBuddy(user.getId(), b));
	}

	public void setBuddyAcceptedWithSecretUserInfo(UserDto actingUser, BuddyConnectResponseMessage connectResponseMessageEntity)
	{
		Buddy buddy = getEntityById(connectResponseMessageEntity.getBuddyId());
		if (buddy.getSendingStatus() == Status.REQUESTED)
		{
			buddy.setSendingStatus(Status.ACCEPTED);
		}
		if (buddy.getReceivingStatus() == Status.REQUESTED)
		{
			buddy.setReceivingStatus(Status.ACCEPTED);
		}
		buddy.setUserAnonymizedId(connectResponseMessageEntity.getRelatedUserAnonymizedId().get());
		buddy.setNickname(connectResponseMessageEntity.getSenderNickname());
		buddy.setFirstName(connectResponseMessageEntity.getFirstName());
		buddy.setLastName(connectResponseMessageEntity.getLastName());
		buddy.setUserPhotoId(connectResponseMessageEntity.getSenderUserPhotoId());
		createBuddyDevices(connectResponseMessageEntity).forEach(buddy::addDevice);
		buddyRepository.save(buddy);
		UUID userAnonymizedId = actingUser.getOwnPrivateData().getUserAnonymizedId();
		userAnonymizedService.updateUserAnonymized(userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId)
				.orElseThrow(() -> InvalidDataException.userAnonymizedIdNotFound(userAnonymizedId)));
	}

	Set<BuddyDto> getBuddyDtos(Set<Buddy> buddyEntities)
	{
		loadAllBuddiesAnonymizedAtOnce(buddyEntities);
		loadAllUsersAnonymizedAtOnce(buddyEntities);
		return buddyEntities.stream().map(this::getBuddy).collect(Collectors.toSet());
	}

	private void loadAllUsersAnonymizedAtOnce(Set<Buddy> buddyEntities)
	{
		UserAnonymized.getRepository().findAllById(buddyEntities.stream().map(Buddy::getUserAnonymizedId)
				.filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));
	}

	private void loadAllBuddiesAnonymizedAtOnce(Set<Buddy> buddyEntities)
	{
		buddyAnonymizedRepository
				.findAllById(buddyEntities.stream().map(Buddy::getBuddyAnonymizedId).collect(Collectors.toList()));
	}

	public Set<UserAnonymizedDto> getBuddyUsersAnonymized(UserAnonymizedDto user)
	{
		return user.getBuddiesAnonymized().stream().filter(ba -> ba.getSendingStatus() == Status.ACCEPTED)
				.map(BuddyAnonymizedDto::getUserAnonymizedId).filter(Optional::isPresent).map(Optional::get)
				.map(buaid -> userAnonymizedService.getUserAnonymized(buaid)).collect(Collectors.toSet());
	}

	public Set<MessageDestination> getBuddyDestinations(UserAnonymized user)
	{
		return user.getBuddiesAnonymized().stream().filter(ba -> ba.getSendingStatus() == Status.ACCEPTED)
				.map(BuddyAnonymized::getUserAnonymized).map(UserAnonymized::getAnonymousDestination).collect(Collectors.toSet());
	}

	private Set<Buddy> getBuddyEntitiesOfUser(UUID forUserId)
	{
		UserDto user = userService.getUser(forUserId);
		return getBuddyEntities(user.getOwnPrivateData().getBuddyIds());
	}

	private Set<Buddy> getBuddyEntities(Set<UUID> buddyIds)
	{
		return buddyIds.stream().map(this::getEntityById).collect(Collectors.toSet());
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
		sendDropBuddyMessage(BuddyInfoParameters.createInstance(requestingUser), message, reason, requestingUserBuddy.getUser());
	}

	private void sendDropBuddyMessage(BuddyInfoParameters buddyInfoParameters, Optional<String> message, DropBuddyReason reason,
			User toUser)
	{
		messageService.sendDirectMessageAndFlushToDatabase(
				BuddyDisconnectMessage.createInstance(buddyInfoParameters, getDropBuddyMessage(reason, message), reason), toUser);
	}

	void sendBuddyConnectResponseMessage(BuddyInfoParameters buddyInfoParameters, UUID receiverUserAnonymizedId, UUID buddyId,
			Set<UserDevice> devices, Status status, String responseMessage)
	{
		UserAnonymizedDto toUser = userAnonymizedService.getUserAnonymized(receiverUserAnonymizedId);
		assert toUser != null;
		messageService.sendMessageAndFlushToDatabase(
				BuddyConnectResponseMessage.createInstance(buddyInfoParameters, responseMessage, buddyId, devices, status),
				toUser);
	}

	private void disconnectBuddyIfConnected(UserAnonymizedDto buddyUserAnonymized, UUID userAnonymizedId)
	{
		Optional<BuddyAnonymizedDto> buddyAnonymized = buddyUserAnonymized.getBuddyAnonymized(userAnonymizedId);
		buddyAnonymized.map(ba -> buddyAnonymizedRepository.findById(ba.getId()).get()).ifPresent(bae -> {
			bae.setDisconnected();
			userAnonymizedService.updateUserAnonymized(buddyUserAnonymized.getId());
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
			String requestingUserName = getFullName(requestingUser.getPrivateData());
			String requestingUserMobileNumber = requestingUser.getMobileNumber();
			String buddyName = getFullName(buddy.getUser().getPrivateData());
			String buddyMobileNumber = buddy.getUser().getMobileNumber();
			Map<String, Object> templateParams = fillTemplateParams(requestingUser, buddy, inviteUrl, requestingUserMobileNumber);
			emailService.sendEmail(requestingUserName, new InternetAddress(buddy.getUser().getEmailAddress(), buddyName),
					subjectTemplateName, bodyTemplateName, templateParams);
			smsService.send(buddyMobileNumber, SmsTemplate.BUDDY_INVITE, templateParams);
		}
		catch (UnsupportedEncodingException e)
		{
			throw EmailException.emailSendingFailed(e);
		}
	}

	private String getFullName(UserPrivateDataBaseDto privateData)
	{
		return StringUtils.join(new Object[] { privateData.getFirstName(), privateData.getLastName() }, " ");
	}

	private Map<String, Object> fillTemplateParams(UserDto requestingUser, BuddyDto buddy, String inviteUrl,
			String requestingUserMobileNumber)
	{
		Map<String, Object> templateParams = new HashMap<>();
		templateParams.put("inviteUrl", inviteUrl);
		templateParams.put("requestingUserFirstName", requestingUser.getPrivateData().getFirstName());
		templateParams.put("requestingUserLastName", requestingUser.getPrivateData().getLastName());
		templateParams.put("requestingUserMobileNumber", requestingUserMobileNumber);
		templateParams.put("requestingUserNickname", requestingUser.getOwnPrivateData().getNickname());
		templateParams.put("buddyFirstName", buddy.getUser().getPrivateData().getFirstName());
		templateParams.put("buddyLastName", buddy.getUser().getPrivateData().getLastName());
		templateParams.put("personalInvitationMessage", buddy.getPersonalInvitationMessage());
		templateParams.put("emailAddress", buddy.getUser().getEmailAddress());
		return templateParams;
	}

	private String getTempPassword()
	{
		return (properties.getEmail().isEnabled()) ? userService.generatePassword() : "abcd";
	}

	private BuddyDto handleBuddyRequestForExistingUser(UUID idOfRequestingUser, BuddyDto buddy)
	{
		UserDto requestingUser = userService.getUser(idOfRequestingUser);
		User buddyUserEntity = UserService.findUserByMobileNumber(buddy.getUser().getMobileNumber());
		buddy.getUser().setUserId(buddyUserEntity.getId());
		Buddy buddyEntity = buddy.createBuddyEntity();
		Buddy savedBuddyEntity = buddyRepository.save(buddyEntity);
		BuddyDto savedBuddy = BuddyDto.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy);

		boolean isRequestingSending = buddy.getReceivingStatus() == Status.REQUESTED;
		boolean isRequestingReceiving = buddy.getSendingStatus() == Status.REQUESTED;
		User requestingUserEntity = userService.getUserEntityById(idOfRequestingUser);
		messageService.sendDirectMessageAndFlushToDatabase(
				BuddyConnectRequestMessage.createInstance(BuddyMessageDto.createBuddyInfoParametersInstance(requestingUser),
						buddy.getPersonalInvitationMessage(), savedBuddyEntity.getId(), requestingUserEntity.getDevices(),
						isRequestingSending, isRequestingReceiving),
				buddyUserEntity);

		return savedBuddy;
	}

	private Buddy getEntityById(UUID id)
	{
		return buddyRepository.findById(id).orElseThrow(() -> BuddyNotFoundException.notFound(id));
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

	public Optional<BuddyDto> getBuddyOfUserByUserAnonymizedId(OwnUserPrivateDataDto user, UUID userAnonymizedId)
	{
		Set<BuddyDto> buddies = user.getBuddies();
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
	public void broadcastUserInfoChangeToBuddies(User updatedUserEntity, UserDto originalUser)
	{
		messageService.broadcastMessageToBuddies(UserAnonymizedDto.createInstance(updatedUserEntity.getAnonymized()),
				() -> BuddyInfoChangeMessage.createInstance(
						BuddyInfoParameters.createInstance(updatedUserEntity, originalUser.getOwnPrivateData().getNickname()),
						getUserInfoChangeMessageText(), updatedUserEntity.getFirstName(), updatedUserEntity.getLastName(),
						updatedUserEntity.getNickname(), updatedUserEntity.getUserPhotoId()));
	}

	private String getUserInfoChangeMessageText()
	{
		return translator.getLocalizedMessage("message.buddy.user.info.changed");
	}

	@Transactional
	public void updateBuddyUserInfo(UUID idOfRequestingUser, UUID relatedUserAnonymizedId, String buddyFirstName,
			String buddyLastName, String buddyNickname, Optional<UUID> buddyUserPhotoId)
	{
		User user = userService.getValidatedUserById(idOfRequestingUser);
		Buddy buddy = user.getBuddyByUserAnonymizedId(relatedUserAnonymizedId);

		buddy.setFirstName(buddyFirstName);
		buddy.setLastName(buddyLastName);
		buddy.setNickname(buddyNickname);
		buddy.setUserPhotoId(buddyUserPhotoId);
		buddyRepository.save(buddy);
	}

	public UserDto getUserOfBuddy(UUID userId, UUID buddyUserId)
	{
		User userEntity = userService.getUserEntityById(userId);
		Buddy buddy = userEntity.getBuddies().stream().filter(b -> b.getUserId().equals(buddyUserId)).findAny()
				.orElseThrow(() -> BuddyNotFoundException.notFoundForUser(userId, buddyUserId));
		return UserDto.createInstance(userEntity, BuddyUserPrivateDataDto.createInstance(buddy));
	}

	@Transactional
	public void processDeviceChange(UUID idOfRequestingUser, UUID relatedUserAnonymizedId, DeviceChange change,
			UUID deviceAnonymizedId, Optional<String> oldName, Optional<String> newName)
	{
		User user = userService.getValidatedUserById(idOfRequestingUser);
		Buddy buddy = user.getBuddyByUserAnonymizedId(relatedUserAnonymizedId);

		switch (change)
		{
			case ADD:
				addDeviceToBuddy(buddy, deviceAnonymizedId, newName.get());
				break;
			case RENAME:
				updateDeviceNameForBuddy(buddy, deviceAnonymizedId, newName.get());
				break;
			case DELETE:
				removeDeviceFromBuddy(buddy, deviceAnonymizedId);
				break;
			default:
				throw new IllegalArgumentException("Change " + change + " is unknown");
		}
	}

	private void addDeviceToBuddy(Buddy buddy, UUID deviceAnonymizedId, String name)
	{
		BuddyDevice device = BuddyDevice.createInstance(name, deviceAnonymizedId);
		buddy.addDevice(device);
	}

	private void updateDeviceNameForBuddy(Buddy buddy, UUID deviceAnonymizedId, String name)
	{
		BuddyDevice device = buddy.getDevices().stream().filter(d -> d.getDeviceAnonymizedId().equals(deviceAnonymizedId))
				.findFirst()
				.orElseThrow(() -> BuddyServiceException.deviceNotFoundByAnonymizedId(buddy.getId(), deviceAnonymizedId));
		device.setName(name);
	}

	private void removeDeviceFromBuddy(Buddy buddy, UUID deviceAnonymizedId)
	{
		BuddyDevice deviceToRemove = buddy.getDevices().stream().filter(d -> d.getDeviceAnonymizedId().equals(deviceAnonymizedId))
				.findAny()
				.orElseThrow(() -> BuddyServiceException.deviceNotFoundByAnonymizedId(buddy.getId(), deviceAnonymizedId));
		buddy.removeDevice(deviceToRemove);
	}
}
