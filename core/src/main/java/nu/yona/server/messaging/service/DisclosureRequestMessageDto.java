/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.entities.DisclosureRequestMessage;
import nu.yona.server.messaging.entities.DisclosureResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;

@JsonRootName("disclosureRequestMessage")
public class DisclosureRequestMessageDto extends BuddyMessageLinkedUserDto
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private final UUID goalId;
	private final LocalDate goalConflictStartTime;
	private final Status status;

	private DisclosureRequestMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message, Status status, Optional<Long> originalGoalConflictMessage, UUID goalId,
			LocalDate goalConflictStartTime)
	{
		super(id, creationTime, isRead, originalGoalConflictMessage, senderInfo, message);
		this.goalId = goalId;
		this.goalConflictStartTime = goalConflictStartTime;
		this.status = status;
	}

	@Override
	public String getType()
	{
		return "DisclosureRequestMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		if (status == Status.DISCLOSURE_REQUESTED)
		{
			possibleActions.add(ACCEPT);
			possibleActions.add(REJECT);
		}
		return possibleActions;
	}

	@JsonIgnore
	public UUID getGoalId()
	{
		return goalId;
	}

	@JsonIgnore
	public LocalDate getGoalConflictStartTime()
	{
		return goalConflictStartTime;
	}

	public Status getStatus()
	{
		return status;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.status == Status.DISCLOSURE_ACCEPTED || this.status == Status.DISCLOSURE_REJECTED;
	}

	public static DisclosureRequestMessageDto createInstance(DisclosureRequestMessage messageEntity, SenderInfo senderInfo)
	{
		GoalConflictMessage targetGoalConflictMessage = messageEntity.getTargetGoalConflictMessage();
		return new DisclosureRequestMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.getStatus(),
				Optional.of(targetGoalConflictMessage.getOriginGoalConflictMessage().getId()),
				targetGoalConflictMessage.getGoal().getId(),
				targetGoalConflictMessage.getActivity().getStartTime().toLocalDate());
	}

	@Component
	private static class Manager extends BuddyMessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private MessageService messageService;

		@Autowired
		private UserAnonymizedService userAnonymizedService;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(DisclosureRequestMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return DisclosureRequestMessageDto.createInstance((DisclosureRequestMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case ACCEPT:
					return handleAction_Accept(actingUser, (DisclosureRequestMessage) messageEntity, requestPayload);
				case REJECT:
					return handleAction_Reject(actingUser, (DisclosureRequestMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDto handleAction_Accept(UserDto actingUser, DisclosureRequestMessage disclosureRequestMessageEntity,
				MessageActionDto payload)
		{
			return updateGoalConflictMessageStatus(actingUser, disclosureRequestMessageEntity,
					GoalConflictMessage.Status.DISCLOSURE_ACCEPTED, payload.getProperty("message"));
		}

		private MessageActionDto handleAction_Reject(UserDto actingUser, DisclosureRequestMessage disclosureRequestMessageEntity,
				MessageActionDto payload)
		{
			return updateGoalConflictMessageStatus(actingUser, disclosureRequestMessageEntity,
					GoalConflictMessage.Status.DISCLOSURE_REJECTED, payload.getProperty("message"));
		}

		private MessageActionDto updateGoalConflictMessageStatus(UserDto actingUser,
				DisclosureRequestMessage disclosureRequestMessageEntity, Status status, String message)
		{
			GoalConflictMessage targetGoalConflictMessage = disclosureRequestMessageEntity.getTargetGoalConflictMessage();
			targetGoalConflictMessage.setStatus(status);
			Message.getRepository().save(targetGoalConflictMessage);

			disclosureRequestMessageEntity = updateMessageStatus(disclosureRequestMessageEntity, status);

			sendResponseMessageToRequestingUser(actingUser, disclosureRequestMessageEntity, message);

			return MessageActionDto
					.createInstanceActionDone(theDtoFactory.createInstance(actingUser, disclosureRequestMessageEntity));
		}

		private DisclosureRequestMessage updateMessageStatus(DisclosureRequestMessage disclosureRequestMessageEntity,
				Status status)
		{
			disclosureRequestMessageEntity.setStatus(status);
			return Message.getRepository().save(disclosureRequestMessageEntity);
		}

		private void sendResponseMessageToRequestingUser(UserDto respondingUser, DisclosureRequestMessage requestMessageEntity,
				String message)
		{
			MessageDestinationDto messageDestination = userAnonymizedService.getUserAnonymized(requestMessageEntity
					.getRelatedUserAnonymizedId()
					.orElseThrow(() -> new IllegalStateException(
							"Message with ID " + requestMessageEntity.getId() + " does not have a related user anonymized ID")))
					.getAnonymousDestination();
			assert messageDestination != null;
			messageService.sendMessageAndFlushToDatabase(
					DisclosureResponseMessage.createInstance(BuddyInfoParameters.createInstance(respondingUser),
							requestMessageEntity.getTargetGoalConflictMessage(), requestMessageEntity.getStatus(), message),
					messageDestination);
		}
	}
}
