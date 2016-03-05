/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

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
		@Type(value = GoalChangeMessageDTO.class, name = "GoalChangeMessage"), })
public abstract class MessageDTO extends PolymorphicDTO
{
	private final UUID id;
	private final Date creationTime;
	private final UUID relatedAnonymousMessageID;

	protected MessageDTO(UUID id, Date creationTime)
	{
		this(id, creationTime, null);
	}

	protected MessageDTO(UUID id, Date creationTime, UUID relatedMessageID)
	{
		this.id = id;
		this.creationTime = creationTime;
		this.relatedAnonymousMessageID = relatedMessageID;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public Date getCreationTime()
	{
		return creationTime;
	}

	@JsonIgnore
	public UUID getRelatedAnonymousMessageID()
	{
		return relatedAnonymousMessageID;
	}

	@JsonIgnore
	public abstract Set<String> getPossibleActions();

	@JsonIgnore
	public abstract boolean canBeDeleted();
}
