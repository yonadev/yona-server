/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import nu.yona.server.properties.PropertyInitializer;

@ComponentScan("nu.yona.server")
@SpringBootApplication
@EnableBatchProcessing
public class DatabaseInitializationApplication
{
	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		SpringApplication app = new SpringApplication(DatabaseInitializationApplication.class);
		app.setWebEnvironment(false);
		app.run(args).close();
	}
}
