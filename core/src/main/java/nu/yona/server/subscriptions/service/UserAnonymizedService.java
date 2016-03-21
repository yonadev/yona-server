/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@CacheConfig(cacheNames = "usersAnonymized")
@Service
public class UserAnonymizedService
{
	@Cacheable
	public UserAnonymizedDTO getUserAnonymized(UUID userAnonymizedID)
	{
		UserAnonymized entity = UserAnonymized.getRepository().findOne(userAnonymizedID);
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
		return UserAnonymized.getRepository().findOne(userAnonymizedID);
	}

	@CachePut(key = "#userAnonymizedID")
	public UserAnonymizedDTO updateUserAnonymized(UUID userAnonymizedID, UserAnonymized entity)
	{
		UserAnonymized savedEntity = UserAnonymized.getRepository().save(entity);
		return UserAnonymizedDTO.createInstance(savedEntity);
	}

	@CacheEvict(key = "#userAnonymizedID")
	public void deleteUserAnonymized(UUID userAnonymizedID)
	{
		UserAnonymized.getRepository().delete(userAnonymizedID);
	}
}
