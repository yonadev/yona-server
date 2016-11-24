/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class BatchServiceApplication
{
	public static void main(String[] args)
	{
		try
		{
			SpringApplication.run(BatchServiceApplication.class, args);
		}
		catch (Exception ex)
		{
			// Issue in Hazelcast: it doesn't shutdown automatically: see https://github.com/hazelcast/hazelcast/issues/6339
			// If service start up fails, we want the the process to exit, so in that case we are shutting down Hazelcast
			// explicitly.
			Hazelcast.shutdownAll();
			throw ex;
		}
	}

	@Bean
	public TaskScheduler taskScheduler()
	{
		return new ConcurrentTaskScheduler(); // single threaded by default
	}
}
