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
	private long activityID;

	/**
	 * Buddy comment messages are always sent in pairs: a message to the buddy and a copy for the user. This property maintains
	 * the relationship between the two.
	 */
	@ManyToOne
	private ActivityCommentMessage senderCopyMessage;

	private ActivityCommentMessage(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname, long activityID,
			boolean isSentItem, String message, Optional<Long> threadHeadMessageID, Optional<Long> repliedMessageID)
	{
		super(senderUserID, senderUserAnonymizedID, senderNickname, isSentItem, message);
		this.activityID = activityID;

		setThreadHeadMessageID(threadHeadMessageID);
		setRepliedMessageID(repliedMessageID);
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

	public long getActivityID()
	{
		return activityID;
	}

	public static ActivityCommentMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			long actitityID, boolean isSentItem, String message, Optional<Long> threadHeadMessageID,
			Optional<Long> repliedMessageID)
	{
		return new ActivityCommentMessage(senderUserID, senderUserAnonymizedID, senderNickname, actitityID, isSentItem, message,
				threadHeadMessageID, repliedMessageID);
	}
}
