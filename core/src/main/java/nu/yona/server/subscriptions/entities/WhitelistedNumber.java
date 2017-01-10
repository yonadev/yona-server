/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "WHITELISTED_NUMBER")
public class WhitelistedNumber extends EntityWithId
{
	public static WhitelistedNumberRepository getRepository()
	{
		return (WhitelistedNumberRepository) RepositoryProvider.getRepository(WhitelistedNumber.class, Long.class);
	}

	@Column(unique = true)
	private String mobileNumber;

	// Default constructor is required for JPA
	public WhitelistedNumber()
	{
		super();
	}

	private WhitelistedNumber(String mobileNumber)
	{
		this.mobileNumber = mobileNumber;
	}

	public static WhitelistedNumber createInstance(String mobileNumber)
	{
		return new WhitelistedNumber(mobileNumber);
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}
}
