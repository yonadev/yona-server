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

@JsonRootName("message")
public abstract class MessageDTO
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
