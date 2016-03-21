/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class ActivityCategoryNotFoundException extends YonaException
{
	private static final long serialVersionUID = -5303422277177893152L;

	protected ActivityCategoryNotFoundException(String messageId, Object... parameters)
	{
		super(HttpStatus.NOT_FOUND, messageId, parameters);
	}

	public static ActivityCategoryNotFoundException notFound(UUID id)
	{
		return new ActivityCategoryNotFoundException("error.activitycategory.not.found", id);
	}

	public static ActivityCategoryNotFoundException notFoundByName(String name)
	{
		return new ActivityCategoryNotFoundException("error.activitycategory.not.found.by.name", name);
	}
}
