/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

import nu.yona.server.properties.PropertyInitializer;

@SpringBootApplication(scanBasePackages = { "nu.yona.server" })
@EnableCaching
public class AdminServiceApplication
{
	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		ConfigurableApplicationContext context = SpringApplication.run(AdminServiceApplication.class, args);
		ApplicationStatusLogger.addLoggerForContextClosedEvent(context);
	}
}
