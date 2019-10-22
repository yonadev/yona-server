/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.GlobalExceptionMapping;

@Service
public class DOSProtectionService
{
	private static final Logger logger = LoggerFactory.getLogger(DOSProtectionService.class);

	@Autowired
	private YonaProperties yonaProperties;

	private LoadingCache<String, Integer> attemptsCache;

	@PostConstruct
	private void initializeCache()
	{
		attemptsCache = CacheBuilder.newBuilder()
				.expireAfterWrite(yonaProperties.getSecurity().getDosProtectionWindow().getSeconds(), TimeUnit.SECONDS)
				.build(new CacheLoader<String, Integer>() {
					@Override
					public Integer load(String key)
					{
						return 0;
					}
				});
	}

	public <T> T executeAttempt(URI uri, HttpServletRequest request, int expectedAttempts, Supplier<T> attempt)
	{
		if (yonaProperties.getSecurity().isDosProtectionEnabled())
		{
			int attempts = increaseAttempts(uri, request);
			delayIfBeyondExpectedAttempts(expectedAttempts, attempts, request);
		}

		return attempt.get();
	}

	private void delayIfBeyondExpectedAttempts(int expectedAttempts, int attempts, HttpServletRequest request)
	{
		if (attempts > expectedAttempts)
		{
			int delayFactor = attempts / expectedAttempts;
			try
			{
				long delaySeconds = delayFactor * 5L;
				logger.warn("Potential DOS attack for {}. Introducing a delay of {} seconds",
						GlobalExceptionMapping.buildRequestInfo(request), delaySeconds);
				Thread.sleep(delaySeconds * 1000L);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private int increaseAttempts(URI uri, HttpServletRequest request)
	{
		String key = getKey(uri, request);
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
		return attempts;
	}

	private String getKey(URI uri, HttpServletRequest request)
	{
		String key = request.getRemoteAddr() + "@" + uri.getPath();
		String query = uri.getQuery();
		if (query != null)
		{
			key += query;
		}
		return key;
	}
}
