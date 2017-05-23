/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.Set;
import java.util.UUID;

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
	 * Buddy comment messages are always sent in pairs: a message to the buddy and a copy for the user. This is one of the two
	 * properties that maintain the relationship between the two messages.
	 */
	@OneToOne(mappedBy = "buddyMessage")
	private ActivityCommentMessage senderCopyMessage;

	/**
	 * Buddy comment messages are always sent in pairs: a message to the buddy and a copy for the user. This is one of the two
	 * properties that maintain the relationship between the two messages.
	 */
	@OneToOne
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

	public static ActivityCommentMessage createThreadHeadInstance(UUID senderUserId, UUID senderUserAnonymizedId,
			String senderNickname, IntervalActivity intervalActivityEntity, boolean isSentItem, String message)
	{
		ActivityCommentMessage activityCommentMessage = createInstance(senderUserId, senderUserAnonymizedId, senderNickname,
				intervalActivityEntity, isSentItem, message);
		// This message is its own little thread
		activityCommentMessage.setThreadHeadMessage(activityCommentMessage);
		return activityCommentMessage;
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
	protected void clearThreadHeadSelfReference()
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

	/**
	 * Helper to explicitly break the cyclic relationship between a buddy message and a sender copy message, to allow deleting
	 * them. Otherwise, the foreign key constraint does not allow deletion of either one.
	 */
	@Override
	public void prepareForDelete()
	{
		super.prepareForDelete();
		buddyMessage = null;
		senderCopyMessage = null;
	}

	@Override
	public Set<Message> getMessagesToBeCascadinglyDeleted()
	{
		// The clean-up is done from the "sender copy side", to prevent recursion.
		if (buddyMessage != null)
		{
			// This is the sender copy, so do the work
			Set<Message> messagesToBeCascadinglyDeleted = getMessagesToBeCascadinglyDeletedForJustThisObject();
			messagesToBeCascadinglyDeleted.addAll(buddyMessage.getMessagesToBeCascadinglyDeletedForJustThisObject());
			messagesToBeCascadinglyDeleted.add(buddyMessage);
			return messagesToBeCascadinglyDeleted;
		}
		// Delegate to the sender copy and add this
		Set<Message> messagesToBeCascadinglyDeleted = senderCopyMessage.getMessagesToBeCascadinglyDeleted();
		messagesToBeCascadinglyDeleted.add(senderCopyMessage);
		return messagesToBeCascadinglyDeleted;
	}

	private Set<Message> getMessagesToBeCascadinglyDeletedForJustThisObject()
	{
		return super.getMessagesToBeCascadinglyDeleted();
	}
}
