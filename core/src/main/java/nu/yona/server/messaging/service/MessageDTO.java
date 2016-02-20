/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.springframework.hateoas.ResourceSupport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.subscriptions.service.BuddyConnectRequestMessageDTO;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDTO;
import nu.yona.server.subscriptions.service.BuddyDisconnectMessageDTO;

@JsonRootName("message")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "@type")
@JsonSubTypes({ @Type(value = BuddyConnectRequestMessageDTO.class, name = "BuddyConnectRequestMessage"),
		@Type(value = BuddyConnectResponseMessageDTO.class, name = "BuddyConnectResponseMessage"),
		@Type(value = BuddyDisconnectMessageDTO.class, name = "BuddyDisconnectMessage"),
		@Type(value = DiscloseRequestMessageDTO.class, name = "DiscloseRequestMessage"),
		@Type(value = DiscloseResponseMessageDTO.class, name = "DiscloseResponseMessage"),
		@Type(value = GoalConflictMessageDTO.class, name = "GoalConflictMessage"), })
public abstract class MessageDTO extends ResourceSupport
{
	private final UUID id;
	private final Date creationTime;
	private final UUID relatedAnonymousMessageID;

	protected MessageDTO(UUID id, Date creationTime)
	{
		this(id, creationTime, null);
	}

	protected MessageDTO(UUID id, Date creationTime, UUID relatedAnonymousMessageID)
	{
		this.id = id;
		this.creationTime = creationTime;
		this.relatedAnonymousMessageID = relatedAnonymousMessageID;
	}

	@JsonProperty(value = "@type")
	public abstract String getType();

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
