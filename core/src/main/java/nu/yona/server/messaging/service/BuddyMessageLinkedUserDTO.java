/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import nu.yona.server.subscriptions.service.UserDTO;

public abstract class BuddyMessageLinkedUserDTO extends BuddyMessageDTO
{

	protected BuddyMessageLinkedUserDTO(UUID id, SenderInfo senderInfo, ZonedDateTime creationTime, boolean isRead,
			Optional<UserDTO> senderUser, String message)
	{
		super(id, senderInfo, creationTime, isRead, senderUser, message);
	}

	protected BuddyMessageLinkedUserDTO(UUID id, SenderInfo senderInfo, ZonedDateTime creationTime, boolean isRead,
			UUID relatedMessageID, Optional<UserDTO> senderUser, String message)
	{
		super(id, senderInfo, creationTime, isRead, relatedMessageID, senderUser, message);
	}
}
