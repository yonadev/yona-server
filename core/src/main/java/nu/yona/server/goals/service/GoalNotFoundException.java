/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such goal")
public class GoalNotFoundException extends RuntimeException
{
	private static final long serialVersionUID = -5303422277177893152L;

	public GoalNotFoundException(UUID id)
	{
		super("Goal with ID '" + id + "' not found");
	}
}
