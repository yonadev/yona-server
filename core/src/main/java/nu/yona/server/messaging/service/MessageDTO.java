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

	protected MessageDTO(UUID id, Date creationTime)
	{
		this.id = id;
		this.creationTime = creationTime;
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

	public abstract Set<String> getPossibleActions();
}
