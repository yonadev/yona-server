/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.DayOfWeek;

import org.junit.Test;

public class WeekActivityTests
{
	@Test
	public void testParse()
	{
		assertThat(WeekActivityDto.parseDate("2016-W02").getDayOfWeek(), equalTo(DayOfWeek.SUNDAY));
		assertThat(WeekActivityDto.parseDate("2016-W02").getYear(), equalTo(2016));
	}
}
