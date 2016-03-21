/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Date;
import java.util.UUID;

import nu.yona.server.subscriptions.service.UserDTO;

public abstract class BuddyMessageLinkedUserDTO extends BuddyMessageDTO
{

	protected BuddyMessageLinkedUserDTO(UUID id, Date creationTime, UserDTO user, String nickname, String message)
	{
		super(id, creationTime, user, nickname, message);
	}

	protected BuddyMessageLinkedUserDTO(UUID id, Date creationTime, UUID relatedMessageID, UserDTO user, String nickname,
			String message)
	{
		super(id, creationTime, relatedMessageID, user, nickname, message);
	}
}
