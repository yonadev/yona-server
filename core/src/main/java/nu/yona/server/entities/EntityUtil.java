/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import org.hibernate.Hibernate;

public class EntityUtil
{
	private EntityUtil()
	{
		// No instances
	}

	@SuppressWarnings("unchecked")
	public static <T> T enforceLoading(T entity)
	{
		return (T) Hibernate.unproxy(entity);
	}
}
