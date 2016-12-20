/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.dbinit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaChecker implements CommandLineRunner
{
	private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaChecker.class);

	@Override
	public void run(String... args) throws Exception
	{
		// The fact that we reach this line implies that the database schema is correct
		logger.info("Database schema is correct");
	}
}
