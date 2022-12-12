/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class EntityWithId
{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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
		{
			return super.hashCode();
		}

		return Long.hashCode(id);
	}

	@Override
	public boolean equals(Object that)
	{
		return (this == that) || (isIdSet() && (that instanceof EntityWithId entityWithId) && getId() == entityWithId.getId());
	}

	private boolean isIdSet()
	{
		return id != 0;
	}
}