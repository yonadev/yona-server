/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.Message;

@Entity
public class ActivityCommentMessage extends BuddyMessage
{
	@ManyToOne
	private IntervalActivity intervalActivity;

	/**
	 * Buddy comment messages are always sent in pairs: a message to the buddy and a copy for the user. This property maintains
	 * the relationship between the two.
	 */
	@OneToOne(mappedBy = "buddyMessage", cascade = CascadeType.REMOVE)
	private ActivityCommentMessage senderCopyMessage;

	@OneToOne(cascade = CascadeType.REMOVE)
	private ActivityCommentMessage buddyMessage;

	private ActivityCommentMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			IntervalActivity intervalActivityEntity, boolean isSentItem, String message)
	{
		super(senderUserId, senderUserAnonymizedId, senderNickname, isSentItem, message);
		this.intervalActivity = intervalActivityEntity;
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

	private void setSenderCopyMessage(ActivityCommentMessage senderCopyMessage)
	{
		this.senderCopyMessage = senderCopyMessage;
	}

	@Override
	public void clearThreadHeadSelfReference()
	{
		Message threadHeadMessage = getThreadHeadMessage();
		if (threadHeadMessage == null || threadHeadMessage.getId() != getId())
		{
			// Prevent recursion
			return;
		}
		super.clearThreadHeadSelfReference();
		if (senderCopyMessage != null)
		{
			senderCopyMessage.clearThreadHeadSelfReference();
		}
		if (buddyMessage != null)
		{
			buddyMessage.clearThreadHeadSelfReference();
		}
	}

	public void setBuddyMessage(ActivityCommentMessage message)
	{
		buddyMessage = message;
		message.setSenderCopyMessage(this);
	}

	public IntervalActivity getIntervalActivity()
	{
		return intervalActivity;
	}

	public static ActivityCommentMessage createThreadHeadInstance(UUID senderUserId, UUID senderUserAnonymizedId,
			String senderNickname, IntervalActivity intervalActivityEntity, boolean isSentItem, String message)
	{
		ActivityCommentMessage activityCommentMessage = createInstance(senderUserId, senderUserAnonymizedId, senderNickname,
				intervalActivityEntity, isSentItem, message);
		// This message is its own little thread
		activityCommentMessage.setThreadHeadMessage(activityCommentMessage);
		return activityCommentMessage;
	}

	public static ActivityCommentMessage createInstance(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			IntervalActivity intervalActivity, boolean isSentItem, String message, Message repliedMessage)
	{
		ActivityCommentMessage activityCommentMessage = createInstance(senderUserId, senderUserAnonymizedId, senderNickname,
				intervalActivity, isSentItem, message);
		repliedMessage.addReply(activityCommentMessage);
		repliedMessage.getThreadHeadMessage().addMessageToThread(activityCommentMessage);
		return activityCommentMessage;
	}

	private static ActivityCommentMessage createInstance(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			IntervalActivity intervalActivityEntity, boolean isSentItem, String message)
	{
		return new ActivityCommentMessage(senderUserId, senderUserAnonymizedId, senderNickname, intervalActivityEntity,
				isSentItem, message);
	}
}
