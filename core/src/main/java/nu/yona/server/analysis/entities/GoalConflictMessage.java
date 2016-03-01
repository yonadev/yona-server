/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
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
	private UUID originGoalConflictMessageID;

	@ManyToOne
	private Activity activity;
	private Status status;

	@Transient
	private String url;
	private byte[] urlCiphertext;

	// Default constructor is required for JPA
	public GoalConflictMessage()
	{
		super(null, null);
	}

	public GoalConflictMessage(UUID id, UUID relatedUserAnonymizedID, UUID originGoalConflictMessageID, Activity activity,
			String url, Status status)
	{
		super(id, relatedUserAnonymizedID);

		this.originGoalConflictMessageID = originGoalConflictMessageID;
		this.activity = activity;
		this.url = url;
		this.status = status;
	}

	public Activity getActivity()
	{
		return activity;
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
				origin.getURL(), Status.ANNOUNCED);
	}

	public static GoalConflictMessage createInstance(UUID relatedUserAnonymizedID, Activity activity, String url)
	{
		return new GoalConflictMessage(UUID.randomUUID(), relatedUserAnonymizedID, null, activity, url, Status.ANNOUNCED);
	}

	public boolean isUrlDisclosed()
	{
		return !isFromBuddy() || getStatus() == Status.DISCLOSURE_ACCEPTED;
	}
}
