/*
 * Copyright (c) 2021 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.cache.CacheMetricsRegistrar;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Tag;

@Component
public class CacheRegistrar
{
	@Autowired(required = false)
	private CacheMetricsRegistrar cacheMetricsRegistrar;

	@Autowired(required = false)
	private CacheManager cacheManager;

	public void registerCacheForMetrics(String cacheName)
	{
		Cache cache = cacheManager.getCache(cacheName);
		Tag t = Tag.of("cacheManager", "cacheManager");
		this.cacheMetricsRegistrar.bindCacheToRegistry(cache, t);
	}

}
