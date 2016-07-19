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
	private UUID activityID;

	private UUID repliedMessageID;

	/**
	 * Buddy comment messages are always sent in pairs: a message to the buddy and a copy for the user. This property maintains
	 * the relationship between the two.
	 */
	@ManyToOne
	private ActivityCommentMessage senderCopyMessage;

	private ActivityCommentMessage(UUID id, UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			UUID activityID, boolean isSentItem, String message, Optional<UUID> repliedMessageID)
	{
		super(id, senderUserID, senderUserAnonymizedID, senderNickname, isSentItem, message);
		this.activityID = activityID;
		this.repliedMessageID = repliedMessageID.orElse(null);
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

	public UUID getActivityID()
	{
		return activityID;
	}

	public Optional<UUID> getRepliedMessageID()
	{
		return Optional.ofNullable(repliedMessageID);
	}

	public static ActivityCommentMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			UUID actitityID, boolean isSentItem, String message, Optional<UUID> repliedMessageID)
	{
		return new ActivityCommentMessage(UUID.randomUUID(), senderUserID, senderUserAnonymizedID, senderNickname, actitityID,
				isSentItem, message, repliedMessageID);
	}
}
