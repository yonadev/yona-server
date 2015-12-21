/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class GoalNotFoundException extends YonaException
{
	private static final long serialVersionUID = -5303422277177893152L;

	protected GoalNotFoundException(String messageId, Object... parameters)
	{
		super(HttpStatus.NOT_FOUND, messageId, parameters);
	}

	public static GoalNotFoundException notFound(UUID id)
	{
		return new GoalNotFoundException("error.goal.not.found", id);
	}

	public static GoalNotFoundException notFoundByName(String name)
	{
		return new GoalNotFoundException("error.goal.not.found.by.name", name);
	}
}
