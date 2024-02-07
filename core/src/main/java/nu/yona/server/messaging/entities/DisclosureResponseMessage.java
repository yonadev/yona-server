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
public class DisclosureResponseMessage extends BuddyMessage
{
	@ManyToOne
	private GoalConflictMessage disclosureResponseTargetGoalConflictMessage;
	@JdbcType(value = IntegerJdbcType.class)
	private Status status;

	// Default constructor is required for JPA
	public DisclosureResponseMessage()
	{

	}

	private DisclosureResponseMessage(BuddyInfoParameters buddyInfoParameters, Status status, String message)
	{
		super(buddyInfoParameters, message);
		this.status = status;
	}

	public static DisclosureResponseMessage createInstance(BuddyInfoParameters buddyInfoParameters,
			GoalConflictMessage targetGoalConflictMessage, Status status, String message)
	{
		DisclosureResponseMessage disclosureResponseMessage = new DisclosureResponseMessage(buddyInfoParameters, status, message);
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
