/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "WHITE_LISTED_NUMBER")
public class WhiteListedNumber extends EntityWithId
{
	public static WhiteListedNumberRepository getRepository()
	{
		return (WhiteListedNumberRepository) RepositoryProvider.getRepository(WhiteListedNumber.class, Long.class);
	}

	@Column(unique = true)
	private String mobileNumber;

	// Default constructor is required for JPA
	public WhiteListedNumber()
	{
		super();
	}

	private WhiteListedNumber(String mobileNumber)
	{
		this.mobileNumber = mobileNumber;
	}

	public static WhiteListedNumber createInstance(String mobileNumber)
	{
		return new WhiteListedNumber(mobileNumber);
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}
}
