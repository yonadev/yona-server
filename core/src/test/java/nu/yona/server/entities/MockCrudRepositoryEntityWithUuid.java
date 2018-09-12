/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

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
	public <S extends T> Iterable<S> saveAll(Iterable<S> entities)
	{
		for (S entity : entities)
		{
			save(entity);
		}
		return entities;
	}

	@Override
	public Optional<T> findById(UUID id)
	{
		return Optional.ofNullable(entities.get(id));
	}

	@Override
	public boolean existsById(UUID id)
	{
		return entities.containsKey(id);
	}

	@Override
	public Iterable<T> findAll()
	{
		return entities.values();
	}

	@Override
	public Iterable<T> findAllById(Iterable<UUID> ids)
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
	public void deleteById(UUID id)
	{
		entities.remove(id);
	}

	@Override
	public void delete(T entity)
	{
		deleteById(entity.getId());
	}

	@Override
	public void deleteAll(Iterable<? extends T> entities)
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
