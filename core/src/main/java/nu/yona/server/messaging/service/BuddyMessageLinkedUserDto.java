/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.Optional;

public abstract class BuddyMessageLinkedUserDto extends BuddyMessageDto
{

	protected BuddyMessageLinkedUserDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message)
	{
		super(id, creationTime, isRead, senderInfo, message);
	}

	protected BuddyMessageLinkedUserDto(long id, LocalDateTime creationTime, boolean isRead, Optional<Long> relatedMessageId,
			SenderInfo senderInfo, String message)
	{
		super(id, creationTime, isRead, senderInfo, relatedMessageId, message);
	}
}
