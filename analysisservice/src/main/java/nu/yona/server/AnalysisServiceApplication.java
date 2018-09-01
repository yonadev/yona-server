/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.metrics.export.prometheus.EnablePrometheusMetrics;

import nu.yona.server.properties.PropertyInitializer;
import nu.yona.server.util.LockPool;

@SpringBootApplication
@EnableCaching
@EnablePrometheusMetrics
public class AnalysisServiceApplication
{
	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		SpringApplication.run(AnalysisServiceApplication.class, args);
	}

	@Bean
	public LockPool<UUID> userAnonymizedSynchronizer()
	{
		return new LockPool<>();
	}
}
