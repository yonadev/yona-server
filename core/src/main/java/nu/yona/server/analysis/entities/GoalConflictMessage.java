/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.messaging.entities.Message;

@Entity
public class GoalConflictMessage extends Message
{
	public enum Status
	{
		ANNOUNCED, DISCLOSE_REQUESTED, DISCLOSE_ACCEPTED, DISCLOSE_REJECTED
	}

	/**
	 * If this is a message sent to a buddy, this refers to the self goal conflict message posted at the user having the goal.
	 * Otherwise {@literal null}.
	 */
	private UUID originGoalConflictMessageID;

	private UUID goalID;
	private Status status;

	@Transient
	private String url;
	private byte[] urlCiphertext;

	private Date endTime;

	// Default constructor is required for JPA
	public GoalConflictMessage()
	{
		super(null, null);
	}

	public GoalConflictMessage(UUID id, UUID relatedUserAnonymizedID, UUID originGoalConflictMessageID, UUID goalID, String url,
			Status status)
	{
		super(id, relatedUserAnonymizedID);

		this.originGoalConflictMessageID = originGoalConflictMessageID;
		this.goalID = goalID;
		this.url = url;
		this.endTime = new Date();
		this.status = status;
	}

	public ActivityCategory getGoal()
	{
		return ActivityCategory.getRepository().findOne(goalID);
	}

	public UUID getOriginGoalConflictMessageID()
	{
		return originGoalConflictMessageID;
	}

	public String getURL()
	{
		return url;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		urlCiphertext = encryptor.encrypt(url);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		url = decryptor.decryptString(urlCiphertext);
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public Date getEndTime()
	{
		return endTime;
	}

	public void setEndTime(Date endTime)
	{
		this.endTime = endTime;
	}

	public UUID getGoalID()
	{
		return goalID;
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
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedID, origin.getID(), origin.getGoalID(),
				origin.getURL(), Status.ANNOUNCED);
	}

	public static GoalConflictMessage createInstance(UUID relatedUserAnonymizedID, ActivityCategory goal, String url)
	{
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedID, null, goal.getID(), url, Status.ANNOUNCED);
	}

	public static GoalConflictMessageRepository getGoalConflictMessageRepository()
	{
		return (GoalConflictMessageRepository) RepositoryProvider.getRepository(GoalConflictMessage.class, UUID.class);
	}

	public boolean isUrlDisclosed()
	{
		return !isFromBuddy() || getStatus() == Status.DISCLOSE_ACCEPTED;
	}
}
