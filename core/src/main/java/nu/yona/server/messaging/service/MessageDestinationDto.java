/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.io.Serializable;
import java.util.UUID;

import nu.yona.server.messaging.entities.MessageDestination;

public class MessageDestinationDto implements Serializable
{
	private static final long serialVersionUID = -3720093259687281141L;

	private UUID id;

	public MessageDestinationDto(UUID id)
	{
		if (id == null)
		{
			throw new IllegalArgumentException("id cannot be null");
		}

		this.id = id;
	}

	public static MessageDestinationDto createInstance(MessageDestination entity)
	{
		return new MessageDestinationDto(entity.getId());
	}

	public UUID getId()
	{
		return id;
	}
}
