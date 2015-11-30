/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("message")
public abstract class MessageDTO
{
	private final UUID id;
	private final UUID relatedAnonymousMessageID;

	protected MessageDTO(UUID id)
	{
		this(id, null);
	}

	protected MessageDTO(UUID id, UUID relatedAnonymousMessageID)
	{
		this.id = id;
		this.relatedAnonymousMessageID = relatedAnonymousMessageID;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonIgnore
	public UUID getRelatedAnonymousMessageID()
	{
		return relatedAnonymousMessageID;
	}

	public abstract Set<String> getPossibleActions();
}
