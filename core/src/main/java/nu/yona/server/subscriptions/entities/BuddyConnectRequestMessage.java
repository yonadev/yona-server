/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Entity;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.goals.entities.Goal;

@Entity
public class BuddyConnectRequestMessage extends BuddyConnectMessage
{

	private BuddyAnonymized.Status status = BuddyAnonymized.Status.NOT_REQUESTED;

	// Default constructor is required for JPA
	public BuddyConnectRequestMessage()
	{
		super();
	}

	private BuddyConnectRequestMessage(UUID id, UUID userID, UUID vpnLoginID, Set<UUID> goalIDs, String nickname, String message,
			UUID buddyID)
	{
		super(id, vpnLoginID, userID, nickname, message, buddyID);
		if (userID == null)
		{
			throw new IllegalArgumentException("requestingUserID cannot be null");
		}
	}

	public boolean isAccepted()
	{
		return status == BuddyAnonymized.Status.ACCEPTED;
	}

	public boolean isRejected()
	{
		return status == BuddyAnonymized.Status.REJECTED;
	}

	public void setStatus(BuddyAnonymized.Status status)
	{
		this.status = status;
	}

	public BuddyAnonymized.Status getStatus()
	{
		return this.status;
	}

	public static BuddyConnectRequestMessage createInstance(UUID requestingUserID, UUID requestingUserVPNLoginID, Set<Goal> goals,
			String nickname, String message, UUID buddyID)
	{
		return new BuddyConnectRequestMessage(UUID.randomUUID(), requestingUserID, requestingUserVPNLoginID,
				goals.stream().map(g -> g.getID()).collect(Collectors.toSet()), nickname, message, buddyID);
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		super.encrypt(encryptor);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		super.decrypt(decryptor);
	}
}
