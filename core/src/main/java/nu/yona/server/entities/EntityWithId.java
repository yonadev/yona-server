/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class EntityWithId
{
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE)
	private long id;

	protected EntityWithId()
	{
	}

	public long getId()
	{
		return id;
	}

	@Override
	public int hashCode()
	{
		if (!isIdSet())
			return super.hashCode();

		return Long.hashCode(id);
	}

	@Override
	public boolean equals(Object that)
	{
		return (this == that) || (isIdSet() && (that instanceof EntityWithId) && getId() == ((EntityWithId) that).getId());
	}

	private boolean isIdSet()
	{
		return id != 0;
	}
}