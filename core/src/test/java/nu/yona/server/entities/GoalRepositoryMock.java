/*******************************************************************************
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Lists;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.test.util.JUnitUtil;

public class GoalRepositoryMock extends MockJpaRepositoryEntityWithUuid<Goal> implements GoalRepository
{
	public static final Field USER_ANONYMIZED_FIELD = JUnitUtil.getAccessibleField(Goal.class, "userAnonymized");

	@Override
	public List<Goal> findByUserAnonymizedId(UUID userAnonymizedId)
	{
		return Lists.newArrayList(super.findAll()).stream().filter(g -> getUserAnonymizedId(g).equals(userAnonymizedId)).toList();
	}

	private UUID getUserAnonymizedId(Goal goal)
	{
		try
		{
			return (UUID) USER_ANONYMIZED_FIELD.get(goal);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
