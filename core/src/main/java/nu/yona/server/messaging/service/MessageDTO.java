/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

import nu.yona.server.Constants;
import nu.yona.server.analysis.service.ActivityCommentMessageDTO;
import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.goals.service.GoalChangeMessageDTO;
import nu.yona.server.rest.PolymorphicDTO;
import nu.yona.server.subscriptions.service.BuddyConnectRequestMessageDTO;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDTO;
import nu.yona.server.subscriptions.service.BuddyDisconnectMessageDTO;

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
	private final UUID id;
	private final ZonedDateTime creationTime;
	private final UUID relatedMessageID;

	protected MessageDTO(UUID id, ZonedDateTime creationTime)
	{
		this(id, creationTime, null);
	}

	protected MessageDTO(UUID id, ZonedDateTime creationTime, UUID relatedMessageID)
	{
		this.id = id;
		this.creationTime = creationTime;
		this.relatedMessageID = relatedMessageID;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public ZonedDateTime getCreationTime()
	{
		return creationTime;
	}

	@JsonIgnore
	public UUID getRelatedMessageID()
	{
		return relatedMessageID;
	}

	@JsonIgnore
	public abstract Set<String> getPossibleActions();

	@JsonIgnore
	public abstract boolean canBeDeleted();
}
