/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import nu.yona.server.properties.PropertyInitializer;
import nu.yona.server.util.LockPool;

@SpringBootApplication
@EnableCaching
public class AnalysisServiceApplication
{
	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		ConfigurableApplicationContext context = SpringApplication.run(AnalysisServiceApplication.class, args);
		ApplicationStatusLogger.addLoggerForContextClosedEvent(context);
	}

	@Bean
	public LockPool<UUID> userAnonymizedSynchronizer()
	{
		return new LockPool<>();
	}
}
