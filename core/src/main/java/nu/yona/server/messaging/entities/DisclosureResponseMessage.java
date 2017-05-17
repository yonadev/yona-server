/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;

@Entity
public class DisclosureResponseMessage extends BuddyMessage
{
	@ManyToOne
	private GoalConflictMessage disclosureResponseTargetGoalConflictMessage;
	private Status status;

	// Default constructor is required for JPA
	public DisclosureResponseMessage()
	{

	}

	private DisclosureResponseMessage(UUID senderUserId, UUID senderUserAnonymizedId, Status status, String senderNickname,
			String message)
	{
		super(senderUserId, senderUserAnonymizedId, senderNickname, message);
		this.status = status;
	}

	public static DisclosureResponseMessage createInstance(UUID senderUserId, UUID senderUserAnonymizedId,
			GoalConflictMessage targetGoalConflictMessage, Status status, String senderNickname, String message)
	{
		DisclosureResponseMessage disclosureResponseMessage = new DisclosureResponseMessage(senderUserId, senderUserAnonymizedId,
				status, senderNickname, message);
		targetGoalConflictMessage.addDisclosureResponse(disclosureResponseMessage);
		return disclosureResponseMessage;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return disclosureResponseTargetGoalConflictMessage;
	}

	public void setTargetGoalConflictMessage(GoalConflictMessage goalConflictMessage)
	{
		this.disclosureResponseTargetGoalConflictMessage = goalConflictMessage;
	}

	public Status getStatus()
	{
		return status;
	}
}
