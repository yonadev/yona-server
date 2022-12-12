/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import java.util.Set;

import org.junit.jupiter.api.Test;

import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.goals.service.GoalDto;

class AnalysisEngineService_determineRelevantGoalsIOsTest extends AnalysisEngineService_determineRelevantGoalsTest
{
	@Override
	protected OperatingSystem getOperatingSystem()
	{
		return OperatingSystem.IOS;
	}

	@Test
	void determineRelevantGoals_networkActivityOneOptionalUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(
				makeNetworkPayload(makeCategorySet(getSocialGoal())));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(getSocialGoal())));
	}
}
