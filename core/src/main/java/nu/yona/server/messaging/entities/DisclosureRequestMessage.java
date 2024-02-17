/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;

@Entity
public class DisclosureRequestMessage extends BuddyMessage
{
	@ManyToOne
	private GoalConflictMessage disclosureRequestTargetGoalConflictMessage;
	@JdbcType(value = IntegerJdbcType.class)
	private Status status;

	// Default constructor is required for JPA
	public DisclosureRequestMessage()
	{
	}

	private DisclosureRequestMessage(BuddyInfoParameters buddyInfoParameters, String message)
	{
		super(buddyInfoParameters, message);
		this.status = Status.DISCLOSURE_REQUESTED;
	}

	public static Message createInstance(BuddyInfoParameters buddyInfoParameters, String message,
			GoalConflictMessage targetGoalConflictMessage)
	{
		DisclosureRequestMessage disclosureRequestMessage = new DisclosureRequestMessage(buddyInfoParameters, message);
		targetGoalConflictMessage.addDisclosureRequest(disclosureRequestMessage);
		return disclosureRequestMessage;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return disclosureRequestTargetGoalConflictMessage;
	}

	public void setTargetGoalConflictMessage(GoalConflictMessage targetGoalConflictMessage)
	{
		this.disclosureRequestTargetGoalConflictMessage = targetGoalConflictMessage;
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}
}
