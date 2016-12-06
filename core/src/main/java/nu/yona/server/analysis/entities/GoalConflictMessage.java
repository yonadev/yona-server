/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
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
	private Optional<Long> originGoalConflictMessageID;

	@ManyToOne
	private Activity activity;
	@ManyToOne
	private Goal goal;
	private Status status;

	@Transient
	private Optional<String> url;
	private byte[] urlCiphertext;

	// Default constructor is required for JPA
	public GoalConflictMessage()
	{
		super(null);
	}

	public GoalConflictMessage(UUID relatedUserAnonymizedID, Optional<Long> originGoalConflictMessageID, Activity activity,
			Goal goal, Optional<String> url, Status status)
	{
		super(relatedUserAnonymizedID);

		this.originGoalConflictMessageID = Objects.requireNonNull(originGoalConflictMessageID);
		this.goal = Objects.requireNonNull(goal);
		this.activity = Objects.requireNonNull(activity);
		this.url = Objects.requireNonNull(url);
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

	public long getOriginGoalConflictMessageID()
	{
		return originGoalConflictMessageID.orElse(getID());
	}

	public Optional<String> getURL()
	{
		return url;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		urlCiphertext = encryptor.encrypt(url.orElse(null));
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		url = Optional.ofNullable(decryptor.decryptString(urlCiphertext));
	}

	public boolean isFromBuddy()
	{
		return originGoalConflictMessageID.isPresent();
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

		assert origin.getID() != 0;
		return new GoalConflictMessage(relatedUserAnonymizedID, Optional.of(origin.getID()), origin.getActivity(),
				origin.getGoal(), origin.getURL(), Status.ANNOUNCED);
	}

	public static GoalConflictMessage createInstance(UUID relatedUserAnonymizedID, Activity activity, Goal goal,
			Optional<String> url)
	{
		return new GoalConflictMessage(relatedUserAnonymizedID, Optional.empty(), activity, goal, url, Status.ANNOUNCED);
	}

	public boolean isUrlDisclosed()
	{
		return !isFromBuddy() || getStatus() == Status.DISCLOSURE_ACCEPTED;
	}
}
