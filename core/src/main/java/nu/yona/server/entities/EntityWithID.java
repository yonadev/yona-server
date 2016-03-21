/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.util.UUID;

import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.hibernate.annotations.Type;

@MappedSuperclass
public abstract class EntityWithID
{
	@Id
	@Type(type = "uuid-char")
	private UUID id;

	/**
	 * This is the only constructor, to ensure that subclasses don't accidentally omit the ID.
	 * 
	 * @param id The ID of the entity
	 */
	protected EntityWithID(UUID id)
	{
		this.id = id;
	}

	public UUID getID()
	{
		return id;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object that)
	{
		return (this == that) || ((that instanceof EntityWithID) && getID().equals(((EntityWithID) that).getID()));
	}
}