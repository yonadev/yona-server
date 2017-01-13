/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import javax.persistence.Entity;

@Entity
public class SystemMessage extends Message
{
	private String message;

	// Default constructor is required for JPA
	protected SystemMessage()
	{
		super(null);
	}

	protected SystemMessage(String message)
	{
		super(null);
		this.message = message;
	}

	public String getMessage()
	{
		return message;
	}

	@Override
	protected void encrypt()
	{
	}

	@Override
	protected void decrypt()
	{
	}

	public static SystemMessage createInstance(String message)
	{
		return new SystemMessage(message);
	}
}
