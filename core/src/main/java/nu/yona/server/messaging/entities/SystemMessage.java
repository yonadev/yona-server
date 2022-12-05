/*******************************************************************************
 * Copyright (c) 2017,2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class SystemMessage extends Message
{
	@Column(length = 255)
	private String message;

	// Default constructor is required for JPA
	protected SystemMessage()
	{
		super();
	}

	private SystemMessage(String message)
	{
		super(Optional.empty());
		this.message = message;
	}

	public static SystemMessage createInstance(String message)
	{
		return new SystemMessage(message);
	}

	public String getMessage()
	{
		return message;
	}

	@Override
	protected void encrypt()
	{
		// It is not necessary to encrypt, the content is public
	}

	@Override
	protected void decrypt()
	{
		// Nothing to do here
	}
}
