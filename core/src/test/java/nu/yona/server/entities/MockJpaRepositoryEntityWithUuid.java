/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.google.common.collect.Lists;

public class MockJpaRepositoryEntityWithUuid<T extends EntityWithUuid> extends MockCrudRepositoryEntityWithUuid<T>
		implements JpaRepository<T, UUID>
{
	@Override
	public Page<T> findAll(Pageable pageable)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> Optional<S> findOne(Example<S> example)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> long count(Example<S> example)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> boolean exists(Example<S> example)
	{
		throw new NotImplementedException();
	}

	@Override
	public List<T> findAll()
	{
		return Lists.newArrayList(super.findAll());
	}

	@Override
	public List<T> findAll(Sort sort)
	{
		throw new NotImplementedException();
	}

	@Override
	public List<T> findAllById(Iterable<UUID> ids)
	{
		List<T> retVal = new ArrayList<>();
		for (T entity : super.findAllById(ids))
		{
			retVal.add(entity);
		}
		return retVal;
	}

	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities)
	{
		return new ArrayList<>(saveAll(entities));
	}

	@Override
	public void flush()
	{
		// No-op
	}

	@Override
	public <S extends T> S saveAndFlush(S entity)
	{
		return save(entity);
	}

	@Override
	@Deprecated
	public void deleteInBatch(Iterable<T> entities)
	{
		deleteAll(entities);
	}

	@Override
	public void deleteAllInBatch()
	{
		deleteAll();
	}

	@Override
	@Deprecated
	public T getOne(UUID id)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> List<S> findAll(Example<S> example)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> List<S> findAll(Example<S> example, Sort sort)
	{
		throw new NotImplementedException();
	}

	@Override
	public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities)
	{
		throw new NotImplementedException();
	}

	@Override
	public void deleteAllInBatch(Iterable<T> entities)
	{
		throw new NotImplementedException();
	}

	@Override
	public void deleteAllByIdInBatch(Iterable<UUID> uuids)
	{
		throw new NotImplementedException();
	}

	@Override
	public T getById(UUID id)
	{
		return findById(id).orElseThrow(() -> new javax.persistence.EntityNotFoundException("ID " + id + " not found"));
	}
}
