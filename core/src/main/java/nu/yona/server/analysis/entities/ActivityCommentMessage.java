/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class ActivityCommentMessage extends BuddyMessage
{
	@ManyToOne
	private IntervalActivity intervalActivity;

	/**
	 * Buddy comment messages are always sent in pairs: a message to the buddy and a copy for the user. This property maintains
	 * the relationship between the two.
	 */
	@ManyToOne
	private ActivityCommentMessage senderCopyMessage;

	private ActivityCommentMessage(UUID id, UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			IntervalActivity intervalActivityEntity, boolean isSentItem, String message, UUID threadHeadMessageId,
			Optional<UUID> repliedMessageId)
	{
		super(id, senderUserId, senderUserAnonymizedId, senderNickname, isSentItem, message);
		this.intervalActivity = intervalActivityEntity;

		setThreadHeadMessageId(threadHeadMessageId);
		setRepliedMessageId(repliedMessageId);
	}

	// Default constructor is required for JPA
	public ActivityCommentMessage()
	{
		super();
	}

	public ActivityCommentMessage getSenderCopyMessage()
	{
		return this.senderCopyMessage;
	}

	public void setSenderCopyMessage(ActivityCommentMessage senderCopyMessage)
	{
		this.senderCopyMessage = senderCopyMessage;
	}

	public IntervalActivity getIntervalActivity()
	{
		return intervalActivity;
	}

	public static ActivityCommentMessage createThreadHeadInstance(UUID senderUserId, UUID senderUserAnonymizedId,
			String senderNickname, IntervalActivity intervalActivityEntity, boolean isSentItem, String message,
			Optional<UUID> repliedMessageId)
	{
		UUID messageId = UUID.randomUUID();
		return createInstance(messageId, senderUserId, senderUserAnonymizedId, senderNickname, intervalActivityEntity, isSentItem,
				message, messageId, repliedMessageId);
	}

	public static ActivityCommentMessage createInstance(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			IntervalActivity intervalActivity, boolean isSentItem, String message, UUID threadHeadMessageId,
			Optional<UUID> repliedMessageId)
	{
		return createInstance(UUID.randomUUID(), senderUserId, senderUserAnonymizedId, senderNickname, intervalActivity,
				isSentItem, message, threadHeadMessageId, repliedMessageId);
	}

	private static ActivityCommentMessage createInstance(UUID id, UUID senderUserId, UUID senderUserAnonymizedId,
			String senderNickname, IntervalActivity intervalActivityEntity, boolean isSentItem, String message,
			UUID threadHeadMessageId, Optional<UUID> repliedMessageId)
	{
		return new ActivityCommentMessage(id, senderUserId, senderUserAnonymizedId, senderNickname, intervalActivityEntity,
				isSentItem, message, threadHeadMessageId, repliedMessageId);
	}
}
