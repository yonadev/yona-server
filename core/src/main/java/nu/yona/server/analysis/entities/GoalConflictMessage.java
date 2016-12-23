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
	private UUID originGoalConflictMessageId;

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

	public GoalConflictMessage(UUID id, UUID relatedUserAnonymizedId, UUID originGoalConflictMessageId, Activity activity,
			Goal goal, Optional<String> url, Status status)
	{
		super(id, relatedUserAnonymizedId);

		this.originGoalConflictMessageId = originGoalConflictMessageId;
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

	public UUID getOriginGoalConflictMessageId()
	{
		return originGoalConflictMessageId;
	}

	public Optional<String> getUrl()
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
		return originGoalConflictMessageId != null;
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public static GoalConflictMessage createInstanceFromBuddy(UUID relatedUserAnonymizedId, GoalConflictMessage origin)
	{
		if (origin == null)
		{
			throw new IllegalArgumentException("origin cannot be null");
		}

		assert origin.getId() != null;
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedId, origin.getId(), origin.getActivity(),
				origin.getGoal(), origin.getUrl(), Status.ANNOUNCED);
	}

	public static GoalConflictMessage createInstance(UUID relatedUserAnonymizedId, Activity activity, Goal goal,
			Optional<String> url)
	{
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedId, null, activity, goal, url, Status.ANNOUNCED);
	}

	public boolean isUrlDisclosed()
	{
		return !isFromBuddy() || getStatus() == Status.DISCLOSURE_ACCEPTED;
	}
}
