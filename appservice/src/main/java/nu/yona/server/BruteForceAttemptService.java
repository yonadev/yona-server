/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import nu.yona.server.properties.YonaProperties;

@Service
public class BruteForceAttemptService
{
	@Autowired
	private YonaProperties yonaProperties;

	private LoadingCache<String, Integer> attemptsCache;

	@PostConstruct
	private void initializeCache()
	{
		attemptsCache = CacheBuilder.newBuilder()
				.expireAfterWrite(yonaProperties.getSecurity().getBruteForceBlockMinutes(), TimeUnit.MINUTES)
				.build(new CacheLoader<String, Integer>() {
					public Integer load(String key)
					{
						return 0;
					}
				});
	}

	public void attemptSucceeded(URI uri, String... otherKeys)
	{
		String key = getKey(uri, otherKeys);
		attemptsCache.invalidate(key);
	}

	public void attemptFailed(URI uri, String... otherKeys)
	{
		String key = getKey(uri, otherKeys);
		int attempts = 0;
		try
		{
			attempts = attemptsCache.get(key);
		}
		catch (ExecutionException e)
		{
			attempts = 0;
		}
		attempts++;
		attemptsCache.put(key, attempts);
	}

	public boolean isBlocked(URI uri, int maxAttempts, Runnable callWhenBlocking, String... otherKeys)
	{
		String key = getKey(uri, otherKeys);
		try
		{
			Integer attempts = attemptsCache.get(key);
			if (attempts == maxAttempts + 1)
			{
				if (callWhenBlocking != null)
				{
					callWhenBlocking.run();
				}
			}
			return attempts >= maxAttempts;
		}
		catch (ExecutionException e)
		{
			return false;
		}
	}

	private String getKey(URI uri, String... otherKeys)
	{
		String key = String.join("~", otherKeys) + "@" + uri.getPath();
		String query = uri.getQuery();
		if (query != null)
		{
			key += query;
		}
		return key;
	}

	public <T> T executeAttempt(URI uri, int maxAttempts, Supplier<T> attempt, String... otherKeys)
	{
		return executeAttempt(uri, maxAttempts, attempt, null, otherKeys);
	}

	public <T> T executeAttempt(URI uri, int maxAttempts, Supplier<T> attempt, Runnable callWhenBlocking, String... otherKeys)
	{
		throwIfBlocked(uri, maxAttempts, callWhenBlocking, otherKeys);

		T result = attemptExecution(uri, attempt, otherKeys);

		attemptSucceeded(uri, otherKeys);
		return result;
	}

	private <T> T attemptExecution(URI uri, Supplier<T> attempt, String... otherKeys)
	{
		try
		{
			return attempt.get();
		}
		catch (RuntimeException e)
		{
			attemptFailed(uri, otherKeys);
			throw e;
		}
	}

	public void throwIfBlocked(URI uri, int maxAttempts, Runnable callWhenBlocking, String... otherKeys)
	{
		if (isBlocked(uri, maxAttempts, callWhenBlocking, otherKeys))
		{
			throw BruteForceException.tooManyAttempts(uri);
		}
	}
}