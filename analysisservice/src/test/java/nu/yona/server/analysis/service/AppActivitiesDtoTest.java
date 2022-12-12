/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AppActivitiesDtoTest
{
	@Test
	void getActivitiesSorted_default_returnsActivitiesOrderedByStartTime()
	{
		ZonedDateTime now = ZonedDateTime.now();
		ZonedDateTime t1 = now;
		ZonedDateTime t2 = t1.plusSeconds(15);
		ZonedDateTime t3 = t2.plusSeconds(15);
		ZonedDateTime t4 = t3.plusSeconds(15);
		AppActivitiesDto.Activity firstActivity = new AppActivitiesDto.Activity("Poker App", t1, t2);
		AppActivitiesDto.Activity secondActivity = new AppActivitiesDto.Activity("Poker App", t3, t4);
		AppActivitiesDto appActivitiesDto = new AppActivitiesDto(now,
				new AppActivitiesDto.Activity[] { secondActivity, firstActivity });

		List<AppActivitiesDto.Activity> result = appActivitiesDto.getActivitiesSorted();

		assertThat(result, equalTo(Arrays.asList(new AppActivitiesDto.Activity[] { firstActivity, secondActivity })));
	}
}