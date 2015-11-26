/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "BUDDIES")
public class BuddyAnonymized extends EntityWithID
{
	public enum Status
	{
		NOT_REQUESTED, REQUESTED, ACCEPTED, REJECTED
	}

	public static BuddyAnonymizedRepository getRepository()
	{
		return (BuddyAnonymizedRepository) RepositoryProvider.getRepository(BuddyAnonymized.class, UUID.class);
	}

	private UUID userAnonymizedID;

	private Status sendingStatus = Status.NOT_REQUESTED;
	private Status receivingStatus = Status.NOT_REQUESTED;

	// Default constructor is required for JPA
	public BuddyAnonymized()
	{
		super(null);
	}

	private BuddyAnonymized(UUID id)
	{
		super(id);
	}

	public static BuddyAnonymized createInstance()
	{
		return new BuddyAnonymized(UUID.randomUUID());
	}

	public UserAnonymized getUserAnonymized()
	{
		if (userAnonymizedID == null)
		{
			throw new IllegalStateException("UserAnonymized is not available in this state");
		}
		return UserAnonymized.getRepository().findOne(userAnonymizedID);
	}

	public void setUserAnonymizedID(UUID userAnonymizedID)
	{
		this.userAnonymizedID = userAnonymizedID;
	}

	public Status getSendingStatus()
	{
		return sendingStatus;
	}

	public void setSendingStatus(Status sendingStatus)
	{
		this.sendingStatus = sendingStatus;
	}

	public Status getReceivingStatus()
	{
		return receivingStatus;
	}

	public void setReceivingStatus(Status receivingStatus)
	{
		this.receivingStatus = receivingStatus;
	}

	public UUID getVPNLoginID()
	{
		return getUserAnonymized().getVPNLoginID();
	}

	public void setVPNLoginID(UUID vpnLoginID)
	{
		setUserAnonymizedID(vpnLoginID);
	}
}
