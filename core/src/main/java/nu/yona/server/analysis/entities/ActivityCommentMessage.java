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

	@ManyToOne
	private ActivityCommentMessage repliedMessage;

	private ActivityCommentMessage(UUID id, UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			UUID activityID, String message, ActivityCommentMessage repliedMessage)
	{
		super(id, senderUserID, senderUserAnonymizedID, senderNickname, message);
		this.activityID = activityID;
		this.repliedMessage = repliedMessage;
	}

	// Default constructor is required for JPA
	public ActivityCommentMessage()
	{
		super();
	}

	public UUID getActivityID()
	{
		return activityID;
	}

	public Optional<ActivityCommentMessage> getRepliedMessage()
	{
		return Optional.ofNullable(repliedMessage);
	}

	public static ActivityCommentMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			UUID actitityID, String message)
	{
		return createInstance(senderUserID, senderUserAnonymizedID, senderNickname, actitityID, message, null);
	}

	public static ActivityCommentMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			UUID actitityID, String message, ActivityCommentMessage repliedMessage)
	{
		return new ActivityCommentMessage(UUID.randomUUID(), senderUserID, senderUserAnonymizedID, senderNickname, actitityID,
				message, repliedMessage);
	}
}
