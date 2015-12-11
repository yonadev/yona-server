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
	public UserAnonymizedDTO getUserAnonymized(UUID vpnLoginID)
	{
		UserAnonymized entity = UserAnonymized.getRepository().findOne(vpnLoginID);
		if (entity == null)
		{
			throw InvalidDataException.vpnLoginIDNotFound(vpnLoginID);
		}
		return UserAnonymizedDTO.createInstance(entity);
	}

	@CachePut(key = "#vpnLoginID")
	public UserAnonymizedDTO updateUserAnonymized(UUID vpnLoginID, UserAnonymized entity)
	{
		UserAnonymized savedEntity = UserAnonymized.getRepository().save(entity);
		return UserAnonymizedDTO.createInstance(savedEntity);
	}

	@CacheEvict(key = "#vpnLoginID")
	public void deleteUserAnonymized(UUID vpnLoginID)
	{
		UserAnonymized.getRepository().delete(vpnLoginID);
	}
}
