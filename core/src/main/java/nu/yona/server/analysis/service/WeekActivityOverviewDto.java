/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("weekActivityOverview")
public class WeekActivityOverviewDto extends IntervalActivityOverviewDto
{
	private final Set<WeekActivityDto> weekActivities;

	private WeekActivityOverviewDto(ZonedDateTime date, Set<WeekActivityDto> weekActivities)
	{
		super(date);
		this.weekActivities = weekActivities;
	}

	@Override
	protected String formatDateAsIso(LocalDate date)
	{
		return WeekActivityDto.formatDate(date);
	}

	@JsonIgnore
	public Set<WeekActivityDto> getWeekActivities()
	{
		return weekActivities;
	}

	static WeekActivityOverviewDto createInstance(ZonedDateTime date, Set<WeekActivityDto> weekActivities)
	{
		return new WeekActivityOverviewDto(date, weekActivities);
	}
}
