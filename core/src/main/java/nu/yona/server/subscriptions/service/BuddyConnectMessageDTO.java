/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.service.MessageDTO;

@JsonRootName("buddyConnectMessage")
public abstract class BuddyConnectMessageDTO extends MessageDTO
{
	private UserDTO user;
	private final String message;

	protected BuddyConnectMessageDTO(UUID id, UserDTO user, String message)
	{
		super(id);
		if (user == null)
		{
			throw new IllegalArgumentException("user cannot be null");
		}
		this.user = user;
		this.message = message;
	}

	public UserDTO getUser()
	{
		return user;
	}

	public String getMessage()
	{
		return message;
	}
}
