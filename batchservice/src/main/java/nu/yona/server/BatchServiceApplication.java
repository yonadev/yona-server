/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import com.hazelcast.core.Hazelcast;

@EnableCaching
@EnableBatchProcessing
@EnableScheduling
@SpringBootApplication(scanBasePackages = { "nu.yona.server" })
public class BatchServiceApplication extends SpringBootServletInitializer
{
	public static void main(String[] args)
	{
		try
		{
			SpringApplication.run(BatchServiceApplication.class, args);
		}
		catch (Exception ex)
		{

			// issue in Hazelcast: it doesn't shutdown automatically,
			// while we want this for the short running database initializer
			// see https://github.com/hazelcast/hazelcast/issues/6339
			Hazelcast.shutdownAll();
			throw ex;
		}
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application)
	{
		return application.sources(BatchServiceApplication.class);
	}

	@Bean
	public TaskScheduler taskScheduler()
	{
		return new ConcurrentTaskScheduler(); // single threaded by default
	}
}
