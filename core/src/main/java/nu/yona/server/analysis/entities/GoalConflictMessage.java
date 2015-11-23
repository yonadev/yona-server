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
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.Message;

@Entity
public class GoalConflictMessage extends Message
{

	private UUID goalID;

	@Transient
	private String url;
	private byte[] urlCiphertext;

	private Date startTime;
	private Date endTime;

	// Default constructor is required for JPA
	public GoalConflictMessage()
	{
		super(null, null);
	}

	public GoalConflictMessage(UUID id, UUID relatedUserAnonymizedID, UUID goalID, String url)
	{
		super(id, relatedUserAnonymizedID);

		this.goalID = goalID;
		this.url = url;
		this.startTime = this.endTime = new Date();
	}

	public Goal getGoal()
	{
		return Goal.getRepository().findOne(goalID);
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

	public Date getStartTime()
	{
		return startTime;
	}

	public void setStartTime(Date startTime)
	{
		this.startTime = startTime;
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

	public static GoalConflictMessage createInstance(UUID loginID, Goal goal, String url)
	{
		return new GoalConflictMessage(UUID.randomUUID(), loginID, goal.getID(), url);
	}

	public static GoalConflictMessageRepository getGoalConflictMessageRepository()
	{
		return (GoalConflictMessageRepository) RepositoryProvider.getRepository(GoalConflictMessage.class, UUID.class);
	}
}
