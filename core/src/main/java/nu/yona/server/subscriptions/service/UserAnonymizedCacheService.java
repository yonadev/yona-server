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
class UserAnonymizedCacheService
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
