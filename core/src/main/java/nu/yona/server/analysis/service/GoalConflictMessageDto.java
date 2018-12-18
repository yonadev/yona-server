/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.DisclosureRequestMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.util.TimeUtil;

@JsonRootName("goalConflictMessage")
public class GoalConflictMessageDto extends MessageDto
{
	private static final String REQUEST_DISCLOSURE = "requestDisclosure";
	private final Optional<String> url;
	private final Status status;
	private final LocalDateTime activityStartTime;
	private final LocalDateTime activityEndTime;
	private final UUID goalId;
	private final UUID activityCategoryId;
	private final LocalDate activityStartDate; // In the user's time zone

	private GoalConflictMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, UUID goalId,
			UUID activityCategoryId, Optional<String> url, Status status, LocalDateTime activityStartTime,
			LocalDateTime activityEndTime, LocalDate activityStartDate)
	{
		super(id, creationTime, isRead, senderInfo);
		this.goalId = goalId;
		this.activityCategoryId = activityCategoryId;
		this.url = url;
		this.status = status;
		this.activityStartTime = activityStartTime;
		this.activityEndTime = activityEndTime;
		this.activityStartDate = activityStartDate;
	}

	@Override
	public String getType()
	{
		return "GoalConflictMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		if (this.isSentFromBuddy() && this.status == Status.ANNOUNCED)
		{
			possibleActions.add(REQUEST_DISCLOSURE);
		}
		return possibleActions;
	}

	@JsonIgnore
	public UUID getGoalId()
	{
		return goalId;
	}

	@JsonIgnore
	public UUID getActivityCategoryId()
	{
		return activityCategoryId;
	}

	public Status getStatus()
	{
		return status;
	}

	public Optional<String> getUrl()
	{
		return url;
	}

	@JsonProperty("activityStartTime")
	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public ZonedDateTime getActivityStartTimeAsZonedDateTime()
	{
		return TimeUtil.toUtcZonedDateTime(activityStartTime);
	}

	public LocalDateTime getActivityStartTime()
	{
		return activityStartTime;
	}

	@JsonProperty("activityEndTime")
	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public ZonedDateTime getActivityEndTimeAsZonedDateTime()
	{
		return TimeUtil.toUtcZonedDateTime(activityEndTime);
	}

	public LocalDateTime getActivityEndTime()
	{
		return activityEndTime;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	private static GoalConflictMessageDto createInstance(GoalConflictMessage messageEntity, SenderInfo senderInfo)
	{
		return new GoalConflictMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getGoal().getId(), messageEntity.getActivity().getActivityCategory().getId(),
				messageEntity.isUrlDisclosed() ? messageEntity.getUrl() : Optional.empty(), messageEntity.getStatus(),
				TimeUtil.toUtcLocalDateTime(messageEntity.getActivity().getStartTimeAsZonedDateTime()),
				TimeUtil.toUtcLocalDateTime(messageEntity.getActivity().getEndTimeAsZonedDateTime()),
				messageEntity.getActivity().getStartTimeAsZonedDateTime().toLocalDate());
	}

	@JsonIgnore
	public LocalDate getActivityStartDate()
	{
		return activityStartDate;
	}

	@Component
	static class Manager extends MessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private UserAnonymizedService userAnonymizedService;

		@Autowired
		private MessageService messageService;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(GoalConflictMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return GoalConflictMessageDto.createInstance((GoalConflictMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case REQUEST_DISCLOSURE:
					return handleAction_RequestDisclosure(actingUser, (GoalConflictMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDto handleAction_RequestDisclosure(UserDto actingUser, GoalConflictMessage messageEntity,
				MessageActionDto requestPayload)
		{
			messageEntity = updateMessageStatusAsDisclosureRequested(messageEntity);

			UserAnonymizedDto toUser = userAnonymizedService.getUserAnonymized(messageEntity.getRelatedUserAnonymizedId().get());
			messageService.sendMessageAndFlushToDatabase(
					DisclosureRequestMessage.createInstance(BuddyMessageDto.createBuddyInfoParametersInstance(actingUser),
							requestPayload.getProperty("message"), messageEntity),
					toUser);

			return MessageActionDto.createInstanceActionDone(theDtoFactory.createInstance(actingUser, messageEntity));
		}

		private GoalConflictMessage updateMessageStatusAsDisclosureRequested(GoalConflictMessage messageEntity)
		{
			messageEntity.setStatus(Status.DISCLOSURE_REQUESTED);
			return GoalConflictMessage.getRepository().save(messageEntity);
		}
	}
}
