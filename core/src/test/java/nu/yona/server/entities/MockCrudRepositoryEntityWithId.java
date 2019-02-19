/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import nu.yona.server.test.util.JUnitUtil;

public class MockCrudRepositoryEntityWithId<T extends EntityWithId> implements CrudRepository<T, Long>
{
	private static final Field idField = JUnitUtil.getAccessibleField(EntityWithId.class, "id");
	private int nextId;
	private Map<Long, T> entities = new HashMap<>();

	@Override
	public <S extends T> S save(S entity)
	{
		try
		{
			idField.setLong(entity, nextId++);
			entities.put(entity.getId(), entity);
			return entity;
		}
		catch (IllegalArgumentException | IllegalAccessException | SecurityException e)
		{
			throw new RuntimeException(e);
		}
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
	public Optional<T> findById(Long id)
	{
		return Optional.ofNullable(entities.get(id));
	}

	@Override
	public boolean existsById(Long id)
	{
		return entities.containsKey(id);
	}

	@Override
	public Iterable<T> findAll()
	{
		return entities.values();
	}

	@Override
	public Iterable<T> findAllById(Iterable<Long> ids)
	{
		List<T> matchingEntities = new ArrayList<>();
		for (Long id : ids)
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
	public void deleteById(Long id)
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
