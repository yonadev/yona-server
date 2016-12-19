/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.Optional;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.Message;

@Entity
public class GoalConflictMessage extends Message
{
	public enum Status
	{
		ANNOUNCED, DISCLOSURE_REQUESTED, DISCLOSURE_ACCEPTED, DISCLOSURE_REJECTED
	}

	/**
	 * If this is a message sent to a buddy, this refers to the self goal conflict message posted at the user having the goal.
	 * Otherwise {@literal null}.
	 */
	@Type(type = "uuid-char")
	private UUID originGoalConflictMessageID;

	@ManyToOne
	private Activity activity;
	@ManyToOne
	private Goal goal;
	private Status status;

	@Transient
	private Optional<String> url;
	@Column(length = 3000)
	private byte[] urlCiphertext;

	// Default constructor is required for JPA
	public GoalConflictMessage()
	{
		super(null, null);
	}

	public GoalConflictMessage(UUID id, UUID relatedUserAnonymizedID, UUID originGoalConflictMessageID, Activity activity,
			Goal goal, Optional<String> url, Status status)
	{
		super(id, relatedUserAnonymizedID);

		this.originGoalConflictMessageID = originGoalConflictMessageID;
		this.goal = goal;
		this.activity = activity;
		this.url = url;
		this.status = status;
	}

	public Activity getActivity()
	{
		return activity;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public UUID getOriginGoalConflictMessageID()
	{
		return originGoalConflictMessageID;
	}

	public Optional<String> getURL()
	{
		return url;
	}

	@Override
	public void encrypt()
	{
		urlCiphertext = CryptoUtil.encryptString(url.orElse(null));
	}

	@Override
	public void decrypt()
	{
		url = Optional.ofNullable(CryptoUtil.decryptString(urlCiphertext));
	}

	public boolean isFromBuddy()
	{
		return originGoalConflictMessageID != null;
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public static GoalConflictMessage createInstanceFromBuddy(UUID relatedUserAnonymizedID, GoalConflictMessage origin)
	{
		if (origin == null)
		{
			throw new IllegalArgumentException("origin cannot be null");
		}

		assert origin.getID() != null;
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedID, origin.getID(), origin.getActivity(),
				origin.getGoal(), origin.getURL(), Status.ANNOUNCED);
	}

	public static GoalConflictMessage createInstance(UUID relatedUserAnonymizedID, Activity activity, Goal goal,
			Optional<String> url)
	{
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedID, null, activity, goal, url, Status.ANNOUNCED);
	}

	public boolean isUrlDisclosed()
	{
		return !isFromBuddy() || getStatus() == Status.DISCLOSURE_ACCEPTED;
	}
}
