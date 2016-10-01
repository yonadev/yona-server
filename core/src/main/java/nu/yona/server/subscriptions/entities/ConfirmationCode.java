/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;

@Entity
@Table(name = "CONFIRMATION_CODES")
public class ConfirmationCode extends EntityWithID
{
	private ZonedDateTime creationTime;
	private String confirmationCode;
	private int attempts;

	// Default constructor is required for JPA
	public ConfirmationCode()
	{
		super(null);
	}

	private ConfirmationCode(UUID id, ZonedDateTime creationTime, String confirmationCode)
	{
		super(id);
		this.creationTime = creationTime;
		this.confirmationCode = confirmationCode;
	}

	public String getConfirmationCode()
	{
		return confirmationCode;
	}

	public void setConfirmationCode(String confirmationCode)
	{
		this.confirmationCode = confirmationCode;
	}

	public ZonedDateTime getCreationTime()
	{
		return creationTime;
	}

	public int getAttempts()
	{
		return attempts;
	}

	public void incrementAttempts()
	{
		this.attempts++;
	}

	public static ConfirmationCode createInstance(String confirmationCode)
	{
		return new ConfirmationCode(UUID.randomUUID(), ZonedDateTime.now(), confirmationCode);
	}
}
