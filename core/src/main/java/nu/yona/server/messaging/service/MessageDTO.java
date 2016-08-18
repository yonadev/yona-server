/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

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
import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.rest.PolymorphicDTO;
import nu.yona.server.subscriptions.service.BuddyConnectRequestMessageDTO;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDTO;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyDisconnectMessageDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("message")
@JsonSubTypes({ @Type(value = BuddyConnectRequestMessageDTO.class, name = "BuddyConnectRequestMessage"),
		@Type(value = BuddyConnectResponseMessageDTO.class, name = "BuddyConnectResponseMessage"),
		@Type(value = BuddyDisconnectMessageDTO.class, name = "BuddyDisconnectMessage"),
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
	private final SenderInfo sender;
	private final ZonedDateTime creationTime;
	private final UUID relatedMessageID;
	private final boolean isRead;

	protected MessageDTO(UUID id, SenderInfo sender, ZonedDateTime creationTime, boolean isRead)
	{
		this(id, sender, creationTime, isRead, null);
	}

	protected MessageDTO(UUID id, SenderInfo sender, ZonedDateTime creationTime, boolean isRead, UUID relatedMessageID)
	{
		this.id = id;
		this.sender = sender;
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
		return sender.isBuddy;
	}

	@JsonProperty("nickname")
	public String getSenderNickname()
	{
		return sender.nickname;
	}

	@JsonIgnore
	public Optional<UUID> getSenderBuddyID()
	{
		return sender.buddyID;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public ZonedDateTime getCreationTime()
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

		protected SenderInfo getSender(UserDTO actingUser, Message messageEntity)
		{
			UUID userAnonymizedID = messageEntity.getRelatedUserAnonymizedID();
			if (actingUser.getPrivateData().getUserAnonymizedID().equals(userAnonymizedID))
			{
				return SenderInfo.createInstanceSelf(actingUser.getID(), actingUser.getPrivateData().getNickname());
			}

			Set<BuddyDTO> buddies = buddyService.getBuddies(actingUser.getPrivateData().getBuddyIDs());
			for (BuddyDTO buddy : buddies)
			{
				if (buddy.getUserAnonymizedID().filter(id -> id.equals(userAnonymizedID)).isPresent())
				{
					return SenderInfo.createInstanceBuddy(buddy.getUser().getID(), buddy.getNickname(), buddy.getID());
				}
			}

			if (messageEntity instanceof BuddyMessage)
			{
				BuddyMessage buddyMessageEntity = (BuddyMessage) messageEntity;
				// this buddy may be not yet connected or no longer connected
				return SenderInfo.createInstanceBuddyNotPresent(buddyMessageEntity.getSenderUserID(),
						buddyMessageEntity.getSenderNickname());
			}

			throw new IllegalStateException("Cannot find buddy for user anonymized ID '" + userAnonymizedID + "'");
		}
	}

	/*
	 * Sender info of the message. The class contents can only be determined when the user reads incoming messages, because buddy
	 * IDs are only known to the user, and for the analysis service sending goal conflicts the user ID and nickname are not known.
	 */
	public final static class SenderInfo
	{
		public final UUID userID;
		public final String nickname;
		public final boolean isBuddy;
		public final Optional<UUID> buddyID;

		private SenderInfo(UUID userID, String nickname, boolean isBuddy, Optional<UUID> buddyID)
		{
			this.userID = userID;
			this.nickname = nickname;
			this.isBuddy = isBuddy;
			this.buddyID = buddyID;
		}

		public static SenderInfo createInstanceBuddy(UUID userID, String nickname, UUID buddyID)
		{
			return new SenderInfo(userID, nickname, true, Optional.of(buddyID));
		}

		public static SenderInfo createInstanceBuddyNotPresent(UUID userID, String nickname)
		{
			return new SenderInfo(userID, nickname, true, Optional.empty());
		}

		public static SenderInfo createInstanceBuddyDetached(UserDTO user, String nickname)
		{
			// user may be null if removed
			return new SenderInfo(user != null ? user.getID() : null, nickname, true, Optional.empty());
		}

		public static SenderInfo createInstanceSelf(UUID userID, String nickname)
		{
			// TODO: return real nickname?
			return new SenderInfo(userID, "<self>", false, Optional.empty());
		}
	}
}
