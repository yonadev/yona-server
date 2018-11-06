/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.CacheConfiguration;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;

@CacheConfig(cacheNames = UserAnonymizedService.CACHE_NAME)
@Service
public class UserAnonymizedService
{
	static final String CACHE_NAME = "usersAnonymized";

	@Autowired(required = false)
	private UserAnonymizedRepository userAnonymizedRepository;

	@Autowired(required = false)
	private CacheConfiguration cacheConfiguration;

	public Set<UserAnonymizedDto> getAllUsersAnonymized()
	{
		return userAnonymizedRepository.findAll().stream().map(UserAnonymizedDto::createInstance).collect(Collectors.toSet());
	}

	@PostConstruct
	public void registerCacheForMetrics()
	{
		if (cacheConfiguration == null)
		{
			// Apparently running in a unit test that does not have all dependencies
			return;
		}
		cacheConfiguration.registerCacheForMetrics(CACHE_NAME);
	}

	@Cacheable
	@Transactional
	public UserAnonymizedDto getUserAnonymized(UUID userAnonymizedId)
	{
		UserAnonymized entity = userAnonymizedRepository.findById(userAnonymizedId)
				.orElseThrow(() -> InvalidDataException.userAnonymizedIdNotFound(userAnonymizedId));
		return UserAnonymizedDto.createInstance(entity);
	}

	/*
	 * Prefer to use other method, because this one is not cached.
	 */
	public Optional<UserAnonymized> getUserAnonymizedEntity(UUID userAnonymizedId)
	{
		return userAnonymizedRepository.findById(userAnonymizedId);
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
		UserAnonymized savedEntity = userAnonymizedRepository.save(userAnonymizedRepository.findById(userAnonymizedId).get());
		return UserAnonymizedDto.createInstance(savedEntity);
	}

	@CacheEvict(key = "#userAnonymizedId")
	public void deleteUserAnonymized(UUID userAnonymizedId)
	{
		userAnonymizedRepository.deleteById(userAnonymizedId);
	}

	@CacheEvict(allEntries = true)
	public void clearCache()
	{
		// Nothing to do here. The annotation ensures the cache is cleared
	}
}
