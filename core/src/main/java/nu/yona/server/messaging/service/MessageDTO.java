/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import nu.yona.server.analysis.service.ActivityCommentMessageDTO;
import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.goals.service.GoalChangeMessageDTO;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.rest.PolymorphicDTO;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyConnectRequestMessageDTO;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDTO;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyDisconnectMessageDTO;
import nu.yona.server.subscriptions.service.BuddyInfoChangeMessageDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.util.TimeUtil;

@JsonRootName("message")
@JsonSubTypes({ @Type(value = BuddyConnectRequestMessageDTO.class, name = "BuddyConnectRequestMessage"),
		@Type(value = BuddyConnectResponseMessageDTO.class, name = "BuddyConnectResponseMessage"),
		@Type(value = BuddyDisconnectMessageDTO.class, name = "BuddyDisconnectMessage"),
		@Type(value = BuddyInfoChangeMessageDTO.class, name = "BuddyInfoChangeMessage"),
		@Type(value = DisclosureRequestMessageDTO.class, name = "DisclosureRequestMessage"),
		@Type(value = DisclosureResponseMessageDTO.class, name = "DisclosureResponseMessage"),
		@Type(value = GoalConflictMessageDTO.class, name = "GoalConflictMessage"),
		@Type(value = GoalChangeMessageDTO.class, name = "GoalChangeMessage"),
		@Type(value = ActivityCommentMessageDTO.class, name = "ActivityCommentMessage"), })
public abstract class MessageDTO extends PolymorphicDTO
{
	private static final String MARK_UNREAD = "markUnread";
	private static final String MARK_READ = "markRead";
	private final UUID id;
	private final LocalDateTime creationTime;
	private final UUID relatedMessageID;
	private final boolean isRead;
	private final SenderInfo senderInfo;

	protected MessageDTO(UUID id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo)
	{
		this(id, senderInfo, creationTime, isRead, null);
	}

	protected MessageDTO(UUID id, SenderInfo senderInfo, LocalDateTime creationTime, boolean isRead, UUID relatedMessageID)
	{
		this.id = id;
		this.senderInfo = senderInfo;
		this.creationTime = creationTime;
		this.isRead = isRead;
		this.relatedMessageID = relatedMessageID;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonIgnore
	public boolean isSentFromBuddy()
	{
		return senderInfo.isBuddy();
	}

	@JsonIgnore
	public Optional<UserDTO> getSenderUser()
	{
		return senderInfo.getUser();
	}

	@JsonProperty("nickname")
	public String getSenderNickname()
	{
		return senderInfo.getNickname();
	}

	@JsonIgnore
	public Optional<UUID> getSenderBuddyID()
	{
		return senderInfo.getBuddy().map(b -> b.getID());
	}

	@JsonProperty("creationTime")
	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
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
	public UUID getRelatedMessageID()
	{
		return relatedMessageID;
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
	protected static abstract class Manager implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private SenderInfo.Factory senderInfoFactory;

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
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

		private MessageActionDTO handleAction_markReadUnread(UserDTO actingUser, Message messageEntity, boolean isRead,
				MessageActionDTO requestPayload)
		{
			messageEntity.setRead(isRead);
			Message savedMessageEntity = Message.getRepository().save(messageEntity);

			return MessageActionDTO.createInstanceActionDone(theDTOFactory.createInstance(actingUser, savedMessageEntity));
		}

		protected final SenderInfo getSenderInfo(UserDTO actingUser, Message messageEntity)
		{
			Optional<UUID> senderUserAnonymizedID = messageEntity.getRelatedUserAnonymizedID();
			if (senderUserAnonymizedID.isPresent())
			{
				if (actingUser.getPrivateData().getUserAnonymizedID().equals(senderUserAnonymizedID.get()))
				{
					return createSenderInfoForSelf(actingUser);
				}

				// Note: actingUser may be out of sync here, because the message action may have changed its state
				// so retrieve afresh from buddy service
				Optional<BuddyDTO> buddy = buddyService.getBuddyOfUserByUserAnonymizedID(actingUser.getID(),
						senderUserAnonymizedID.get());
				if (buddy.isPresent())
				{
					return createSenderInfoForBuddy(buddy.get());
				}
			}

			return getSenderInfoExtensionPoint(messageEntity);
		}

		protected SenderInfo getSenderInfoExtensionPoint(Message messageEntity)
		{
			throw new IllegalStateException("Cannot find buddy for message of type '" + messageEntity.getClass().getName()
					+ "' with user anonymized ID '" + messageEntity.getRelatedUserAnonymizedID() + "'");
		}

		private SenderInfo createSenderInfoForSelf(UserDTO actingUser)
		{
			return senderInfoFactory.createInstanceForSelf(actingUser.getID(), actingUser.getPrivateData().getNickname());
		}

		private SenderInfo createSenderInfoForBuddy(BuddyDTO buddy)
		{
			return senderInfoFactory.createInstanceForBuddy(buddy.getUser().getID(), buddy.getNickname(), buddy.getID());
		}

		protected SenderInfo createSenderInfoForDetachedBuddy(Optional<User> userEntity, String nickname)
		{
			return senderInfoFactory.createInstanceForDetachedBuddy(UserDTO.createInstance(userEntity), nickname);
		}
	}
}
