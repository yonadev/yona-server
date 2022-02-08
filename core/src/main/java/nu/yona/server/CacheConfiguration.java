/*******************************************************************************
 * Copyright (c) 2018-2022 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;

@Configuration
public class CacheConfiguration extends CachingConfigurerSupport
{
	@Autowired
	private YonaProperties yonaProperties;

	@Override
	@Bean
	public CacheManager cacheManager()
	{
		return new HazelcastCacheManager(hazelcastInstance());
	}

	@Bean
	public HazelcastInstance hazelcastInstance()
	{
		String hazelcastConfigFilePath = yonaProperties.getHazelcastConfigFilePath();
		if (hazelcastConfigFilePath == null)
		{
			Config config = new Config();
			config.getNetworkConfig().getJoin().getMulticastConfig().setMulticastPort(61234);
			return Hazelcast.newHazelcastInstance(config);
		}
		return getHazelcastClientInstance(hazelcastConfigFilePath);
	}

	private HazelcastInstance getHazelcastClientInstance(String hazelcastConfigFilePath)
	{
		try
		{
			return HazelcastClient.newHazelcastClient(new XmlClientConfigBuilder(hazelcastConfigFilePath).build());
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	public CacheManager localCache()
	{
		return new ConcurrentMapCacheManager();
	}
}
