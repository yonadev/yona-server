/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.util.Objects;

public class PropertyInitializer
{
	// No instances
	private PropertyInitializer()
	{
	}

	public static void initializePropertiesFromEnvironment()
	{
		String dbUserName = System.getenv("YONA_DB_USER_NAME");
		String dbPassword = System.getenv("YONA_DB_PASSWORD");
		String dbUrl = System.getenv("YONA_DB_URL");

		setPropertyIfNotSet("spring.datasource.url", dbUrl);
		setPropertyIfNotSet("spring.datasource.username", dbUserName);
		setPropertyIfNotSet("spring.datasource.password", dbPassword);

		setPropertyIfNotSet("batch.jdbc.url", dbUrl);
		setPropertyIfNotSet("batch.jdbc.user", dbUserName);
		setPropertyIfNotSet("batch.jdbc.password", dbPassword);
	}

	private static void setPropertyIfNotSet(String key, String value)
	{
		if (System.getProperty(key) != null)
		{
			return;
		}

		System.setProperty(key, Objects.requireNonNull(value, "Value for property " + key));
	}
}
