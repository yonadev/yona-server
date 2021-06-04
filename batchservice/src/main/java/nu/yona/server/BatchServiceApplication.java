/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.util.List;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import nu.yona.server.properties.PropertyInitializer;

@EnableCaching
@EnableBatchProcessing
@EnableScheduling
@SpringBootApplication(scanBasePackages = { "nu.yona.server" })
public class BatchServiceApplication
{
	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		ConfigurableApplicationContext context = SpringApplication.run(BatchServiceApplication.class, args);
		ApplicationStatusLogger.addLoggerForContextClosedEvent(context);
	}

	@Bean
	public TaskScheduler taskScheduler()
	{
		return new ConcurrentTaskScheduler(); // single threaded by default
	}

	@Bean(name = "noopItemWriter")
	public ItemWriter<Object> noopItemWriter()
	{
		return new ItemWriter<Object>()
		{

			@Override
			public void write(List<? extends Object> items) throws Exception
			{
				// Noop-implementation, so nothing to do here
			}
		};
	}
}
