/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

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

	@Cacheable
	public UserAnonymizedDTO getUserAnonymized(UUID userAnonymizedID)
	{
		UserAnonymized entity = userAnonymizedRepository.findOne(userAnonymizedID);
		if (entity == null)
		{
			throw InvalidDataException.userAnonymizedIDNotFound(userAnonymizedID);
		}
		return UserAnonymizedDTO.createInstance(entity);
	}

	/*
	 * Prefer to use other method, because this one is not cached.
	 */
	public UserAnonymized getUserAnonymizedEntity(UUID userAnonymizedID)
	{
		return userAnonymizedRepository.findOne(userAnonymizedID);
	}

	@CachePut(key = "#userAnonymizedID")
	public UserAnonymizedDTO updateUserAnonymized(UUID userAnonymizedID, UserAnonymized entity)
	{
		UserAnonymized savedEntity = userAnonymizedRepository.save(entity);
		return UserAnonymizedDTO.createInstance(savedEntity);
	}

	@CacheEvict(key = "#userAnonymizedID")
	public UserAnonymized updateUserAnonymized(UserAnonymized entity)
	{
		return userAnonymizedRepository.saveAndFlush(entity);
	}

	@CacheEvict(key = "#userAnonymizedID")
	public void deleteUserAnonymized(UUID userAnonymizedID)
	{
		userAnonymizedRepository.delete(userAnonymizedID);
	}
}
