/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;

import org.hibernate.annotations.Type;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;

@Entity
public class DisclosureRequestMessage extends BuddyMessage
{
	@Type(type = "uuid-char")
	private UUID targetGoalConflictMessageID;
	private Status status;

	// Default constructor is required for JPA
	public DisclosureRequestMessage()
	{
	}

	private DisclosureRequestMessage(UUID id, UUID senderUserID, UUID senderUserAnonymizedID, UUID targetGoalConflictMessageID,
			String senderUserNickname, String message)
	{
		super(id, senderUserID, senderUserAnonymizedID, senderUserNickname, message);
		this.targetGoalConflictMessageID = targetGoalConflictMessageID;
		this.status = Status.DISCLOSURE_REQUESTED;
	}

	public UUID getTargetGoalConflictMessageID()
	{
		return targetGoalConflictMessageID;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return (GoalConflictMessage) GoalConflictMessage.getRepository().findOne(targetGoalConflictMessageID);
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public static Message createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderUserNickname,
			String message, GoalConflictMessage targetGoalConflictMessage)
	{
		return new DisclosureRequestMessage(UUID.randomUUID(), senderUserID, senderUserAnonymizedID,
				targetGoalConflictMessage.getID(), senderUserNickname, message);
	}
}
