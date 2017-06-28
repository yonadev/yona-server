/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

import nu.yona.server.Constants;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.analysis.service.GoalConflictMessageDto;
import nu.yona.server.goals.service.GoalChangeMessageDto;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DtoManager;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.rest.PolymorphicDto;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyConnectRequestMessageDto;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDto;
import nu.yona.server.subscriptions.service.BuddyDisconnectMessageDto;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyInfoChangeMessageDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.util.TimeUtil;

@JsonRootName("message")
@JsonSubTypes({ @Type(value = BuddyConnectRequestMessageDto.class, name = "BuddyConnectRequestMessage"),
		@Type(value = BuddyConnectResponseMessageDto.class, name = "BuddyConnectResponseMessage"),
		@Type(value = BuddyDisconnectMessageDto.class, name = "BuddyDisconnectMessage"),
		@Type(value = BuddyInfoChangeMessageDto.class, name = "BuddyInfoChangeMessage"),
		@Type(value = DisclosureRequestMessageDto.class, name = "DisclosureRequestMessage"),
		@Type(value = DisclosureResponseMessageDto.class, name = "DisclosureResponseMessage"),
		@Type(value = GoalConflictMessageDto.class, name = "GoalConflictMessage"),
		@Type(value = GoalChangeMessageDto.class, name = "GoalChangeMessage"),
		@Type(value = ActivityCommentMessageDto.class, name = "ActivityCommentMessage"),
		@Type(value = SystemMessageDto.class, name = "SystemMessage"), })
public abstract class MessageDto extends PolymorphicDto
{
	private static final String MARK_UNREAD = "markUnread";
	private static final String MARK_READ = "markRead";
	private final long id;
	private final LocalDateTime creationTime;
	private final Optional<Long> relatedMessageId;
	private final boolean isRead;
	private final SenderInfo senderInfo;

	protected MessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo)
	{
		this(id, senderInfo, creationTime, isRead, Optional.empty());
	}

	protected MessageDto(long id, SenderInfo senderInfo, LocalDateTime creationTime, boolean isRead,
			Optional<Long> relatedMessageId)
	{
		this.id = id;
		this.senderInfo = senderInfo;
		this.creationTime = creationTime;
		this.isRead = isRead;
		this.relatedMessageId = relatedMessageId;
	}

	@JsonIgnore
	public long getMessageId()
	{
		return id;
	}

	@JsonIgnore
	public boolean isSentFromBuddy()
	{
		return senderInfo.isBuddy();
	}

	@JsonIgnore
	public Optional<UserDto> getSenderUser()
	{
		return senderInfo.getUser();
	}

	@JsonProperty("nickname")
	public String getSenderNickname()
	{
		return senderInfo.getNickname();
	}

	@JsonIgnore
	public Optional<UUID> getSenderBuddyId()
	{
		return senderInfo.getBuddy().map(BuddyDto::getId);
	}

	@JsonProperty("creationTime")
	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public ZonedDateTime getCreationTimeAsZonedDateTime()
	{
		return TimeUtil.toUtcZonedDateTime(creationTime);
	}

	public LocalDateTime getCreationTime()
	{
		return creationTime;
	}

	@JsonProperty("isRead")
	public boolean isRead()
	{
		return isRead;
	}

	@JsonIgnore
	public Optional<Long> getRelatedMessageId()
	{
		return relatedMessageId;
	}

	@JsonIgnore
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		possibleActions.add((isRead) ? MARK_UNREAD : MARK_READ);
		return possibleActions;
	}

	@JsonIgnore
	public abstract boolean canBeDeleted();

	@Component
	protected abstract static class Manager implements DtoManager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private SenderInfo.Factory senderInfoFactory;

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case MARK_READ:
					return handleAction_markReadUnread(actingUser, messageEntity, true, requestPayload);
				case MARK_UNREAD:
					return handleAction_markReadUnread(actingUser, messageEntity, false, requestPayload);
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDto handleAction_markReadUnread(UserDto actingUser, Message messageEntity, boolean isRead,
				MessageActionDto requestPayload)
		{
			messageEntity.setRead(isRead);
			Message savedMessageEntity = Message.getRepository().save(messageEntity);

			return MessageActionDto.createInstanceActionDone(theDtoFactory.createInstance(actingUser, savedMessageEntity));
		}

		protected final SenderInfo getSenderInfo(UserDto actingUser, Message messageEntity)
		{
			Optional<UUID> senderUserAnonymizedId = getSenderUserAnonymizedId(actingUser, messageEntity);
			if (senderUserAnonymizedId.isPresent())
			{
				if (actingUser.getPrivateData().getUserAnonymizedId().equals(senderUserAnonymizedId.get()))
				{
					return createSenderInfoForSelf(actingUser);
				}

				Optional<BuddyDto> buddy = buddyService.getBuddyOfUserByUserAnonymizedId(actingUser.getPrivateData(),
						senderUserAnonymizedId.get());
				if (buddy.isPresent())
				{
					return createSenderInfoForBuddy(buddy.get());
				}
			}

			return getSenderInfoExtensionPoint(messageEntity);
		}

		protected Optional<UUID> getSenderUserAnonymizedId(UserDto actingUser, Message messageEntity)
		{
			return messageEntity.getRelatedUserAnonymizedId();
		}

		protected SenderInfo getSenderInfoExtensionPoint(Message messageEntity)
		{
			throw new IllegalStateException(
					"Cannot find buddy for message of type '" + messageEntity.getClass().getName() + "' with user anonymized ID '"
							+ messageEntity.getRelatedUserAnonymizedId().map(UUID::toString).orElse("UNKNOWN") + "'");
		}

		private SenderInfo createSenderInfoForSelf(UserDto actingUser)
		{
			return senderInfoFactory.createInstanceForSelf(actingUser.getId(), actingUser.getPrivateData().getNickname());
		}

		private SenderInfo createSenderInfoForBuddy(BuddyDto buddy)
		{
			return senderInfoFactory.createInstanceForBuddy(buddy.getUser().getId(), buddy.getNickname(), buddy.getId());
		}

		protected SenderInfo createSenderInfoForDetachedBuddy(Optional<User> userEntity, String nickname)
		{
			return senderInfoFactory.createInstanceForDetachedBuddy(UserDto.createInstance(userEntity), nickname);
		}

		protected SenderInfo createSenderInfoForSystem()
		{
			return senderInfoFactory.createInstanceForSystem();
		}
	}
}
