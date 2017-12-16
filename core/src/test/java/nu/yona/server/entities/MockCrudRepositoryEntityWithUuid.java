/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import nu.yona.server.entities.EntityWithUuid;

public class MockCrudRepositoryEntityWithUuid<T extends EntityWithUuid> implements CrudRepository<T, UUID>
{
	private Map<UUID, T> entities = new HashMap<>();

	@Override
	public <S extends T> S save(S entity)
	{
		entities.put(entity.getId(), entity);
		return entity;
	}

	@Override
	public <S extends T> Iterable<S> save(Iterable<S> entities)
	{
		for (S entity : entities)
		{
			save(entity);
		}
		return entities;
	}

	@Override
	public T findOne(UUID id)
	{
		return entities.get(id);
	}

	@Override
	public boolean exists(UUID id)
	{
		return entities.containsKey(id);
	}

	@Override
	public Iterable<T> findAll()
	{
		return entities.values();
	}

	@Override
	public Iterable<T> findAll(Iterable<UUID> ids)
	{
		List<T> matchingEntities = new ArrayList<>();
		for (UUID id : ids)
		{
			T entity = entities.get(id);
			if (entity != null)
			{
				matchingEntities.add(entity);
			}
		}
		return matchingEntities;
	}

	@Override
	public long count()
	{
		return entities.size();
	}

	@Override
	public void delete(UUID id)
	{
		entities.remove(id);
	}

	@Override
	public void delete(T entity)
	{
		delete(entity.getId());
	}

	@Override
	public void delete(Iterable<? extends T> entities)
	{
		for (T entity : entities)
		{
			delete(entity);
		}
	}

	@Override
	public void deleteAll()
	{
		entities.clear();
	}
}
