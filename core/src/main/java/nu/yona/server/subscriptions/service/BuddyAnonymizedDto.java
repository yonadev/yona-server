/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

public class BuddyAnonymizedDto implements Serializable
{
	private static final long serialVersionUID = -7833488505462396996L;

	private final UUID id;
	private final UUID userAnonymizedId;
	private final Status sendingStatus;
	private final Status receivingStatus;

	public BuddyAnonymizedDto(UUID id, Optional<UUID> userAnonymizedId, Status sendingStatus, Status receivingStatus)
	{
		this.id = id;
		this.userAnonymizedId = userAnonymizedId.orElse(null);
		this.sendingStatus = sendingStatus;
		this.receivingStatus = receivingStatus;
	}

	public static BuddyAnonymizedDto createInstance(BuddyAnonymized entity)
	{
		return new BuddyAnonymizedDto(entity.getId(), entity.getUserAnonymizedId(), entity.getSendingStatus(),
				entity.getReceivingStatus());
	}

	public UUID getId()
	{
		return id;
	}

	public Optional<UUID> getUserAnonymizedId()
	{
		return Optional.ofNullable(userAnonymizedId);
	}

	public Status getSendingStatus()
	{
		return sendingStatus;
	}

	public Status getReceivingStatus()
	{
		return receivingStatus;
	}

	public boolean isAccepted()
	{
		return (getReceivingStatus() == Status.ACCEPTED) || (getSendingStatus() == Status.ACCEPTED);
	}
}
