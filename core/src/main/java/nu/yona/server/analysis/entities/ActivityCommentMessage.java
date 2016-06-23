/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class ActivityCommentMessage extends BuddyMessage
{
	private UUID activityID;

	public ActivityCommentMessage(UUID id, UUID senderUserID, UUID senderAnonymizedUserID, String senderNickname, UUID activityID,
			String message)
	{
		super(id, senderUserID, senderAnonymizedUserID, senderNickname, message);
		this.activityID = activityID;
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

	public static ActivityCommentMessage createInstance(UUID senderUserID, UUID senderAnonymizedUserID, String senderNickname,
			UUID actitityID, String message)
	{
		return new ActivityCommentMessage(UUID.randomUUID(), senderUserID, senderAnonymizedUserID, senderNickname, actitityID,
				message);
	}
}
