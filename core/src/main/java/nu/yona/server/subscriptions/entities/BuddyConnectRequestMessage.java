/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import jakarta.persistence.Entity;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.service.BuddyServiceException;

@Entity
public class BuddyConnectRequestMessage extends BuddyConnectMessage
{
	private boolean isRequestingSending;
	private boolean isRequestingReceiving;

	@JdbcType(value = IntegerJdbcType.class)
	private Status status;

	// Default constructor is required for JPA
	public BuddyConnectRequestMessage()
	{
		super();
	}

	private BuddyConnectRequestMessage(BuddyInfoParameters buddyInfoParameters, String message, UUID buddyId,
			Set<UserDevice> devices, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		super(buddyInfoParameters, message, buddyId, devices);

		if (buddyInfoParameters.userId == null)
		{
			throw BuddyServiceException.requestingUserCannotBeNull();
		}

		this.status = Status.REQUESTED;
		this.isRequestingSending = isRequestingSending;
		this.isRequestingReceiving = isRequestingReceiving;
	}

	/**
	 * Copy constructor. See {@link nu.yona.server.messaging.entities.Message#duplicate()}
	 *
	 * @param original Message to copy.
	 */
	public BuddyConnectRequestMessage(BuddyConnectRequestMessage original)
	{
		super(original);
		this.isRequestingSending = original.isRequestingSending;
		this.isRequestingReceiving = original.isRequestingReceiving;
		this.status = original.status;
	}

	public static BuddyConnectRequestMessage createInstance(BuddyInfoParameters buddyInfoParameters, String message, UUID buddyId,
			Set<UserDevice> devices, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		return new BuddyConnectRequestMessage(buddyInfoParameters, message, buddyId, devices, isRequestingSending,
				isRequestingReceiving);
	}

	@Override
	public boolean isUserFetchable()
	{
		return status == Status.ACCEPTED;
	}

	public boolean requestingSending()
	{
		return isRequestingSending;
	}

	public boolean requestingReceiving()
	{
		return isRequestingReceiving;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public Status getStatus()
	{
		return this.status;
	}
}
