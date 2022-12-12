/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class EntityWithUuid
{
	@Id
	@JdbcTypeCode(java.sql.Types.VARCHAR)
	private final UUID id;

	/**
	 * This is the only constructor, to ensure that subclasses don't accidentally omit the ID.
	 *
	 * @param id The ID of the entity
	 */
	protected EntityWithUuid(UUID id)
	{
		this.id = id;
	}

	public static UUID getIdWithoutLoadingEntity(EntityWithUuid entity)
	{
		if (entity instanceof HibernateProxy)
		{
			return (UUID) ((HibernateProxy) entity).getHibernateLazyInitializer().getIdentifier();
		}
		return entity.getId();
	}

	public UUID getId()
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
		return (this == that) || ((that instanceof EntityWithUuid entityWithUuid) && getId().equals(entityWithUuid.getId()));
	}
}
