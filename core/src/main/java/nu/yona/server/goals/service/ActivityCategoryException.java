/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class ActivityCategoryException extends YonaException
{
	private static final long serialVersionUID = -5303422277177893152L;

	protected ActivityCategoryException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	protected ActivityCategoryException(HttpStatus statusCode, String messageId, Object... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static ActivityCategoryException notFound(UUID id)
	{
		return new ActivityCategoryException(HttpStatus.NOT_FOUND, "error.activitycategory.not.found", id);
	}

	public static ActivityCategoryException duplicateName(Locale locale, String name)
	{
		return new ActivityCategoryException("error.activitycategory.duplicate.name", locale.toLanguageTag(), name);
	}
}
