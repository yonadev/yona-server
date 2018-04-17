/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;

@CacheConfig(cacheNames = "usersAnonymized")
@Service
public class UserAnonymizedService
{
	@Autowired(required = false)
	private UserAnonymizedRepository userAnonymizedRepository;

	public Set<UserAnonymizedDto> getAllUsersAnonymized()
	{
		return userAnonymizedRepository.findAll().stream().map(UserAnonymizedDto::createInstance).collect(Collectors.toSet());
	}

	@Cacheable
	@Transactional
	public UserAnonymizedDto getUserAnonymized(UUID userAnonymizedId)
	{
		UserAnonymized entity = userAnonymizedRepository.findOne(userAnonymizedId);
		if (entity == null)
		{
			throw InvalidDataException.userAnonymizedIdNotFound(userAnonymizedId);
		}
		return UserAnonymizedDto.createInstance(entity);
	}

	/*
	 * Prefer to use other method, because this one is not cached.
	 */
	public UserAnonymized getUserAnonymizedEntity(UUID userAnonymizedId)
	{
		return userAnonymizedRepository.findOne(userAnonymizedId);
	}

	@CachePut(key = "#userAnonymized.id")
	public UserAnonymizedDto updateUserAnonymized(UserAnonymized userAnonymized)
	{
		UserAnonymized savedEntity = userAnonymizedRepository.save(userAnonymized);
		return UserAnonymizedDto.createInstance(savedEntity);
	}

	@CachePut(key = "#userAnonymizedId")
	public UserAnonymizedDto updateUserAnonymized(UUID userAnonymizedId)
	{
		UserAnonymized savedEntity = userAnonymizedRepository.save(userAnonymizedRepository.findOne(userAnonymizedId));
		return UserAnonymizedDto.createInstance(savedEntity);
	}

	@CacheEvict(key = "#userAnonymizedId")
	public void deleteUserAnonymized(UUID userAnonymizedId)
	{
		userAnonymizedRepository.delete(userAnonymizedId);
	}

	@CacheEvict(allEntries = true)
	public void clearCache()
	{
		// Nothing to do here. The annotation ensures the cache is cleared
	}
}
