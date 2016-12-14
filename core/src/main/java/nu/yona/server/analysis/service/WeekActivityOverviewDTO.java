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
public class WeekActivityOverviewDTO extends IntervalActivityOverviewDTO
{
	private final Set<WeekActivityDTO> weekActivities;

	private WeekActivityOverviewDTO(ZonedDateTime date, Set<WeekActivityDTO> weekActivities)
	{
		super(date);
		this.weekActivities = weekActivities;
	}

	@Override
	protected String formatDateAsIso(LocalDate date)
	{
		return WeekActivityDTO.formatDate(date);
	}

	@JsonIgnore
	public Set<WeekActivityDTO> getWeekActivities()
	{
		return weekActivities;
	}

	static WeekActivityOverviewDTO createInstance(ZonedDateTime date, Set<WeekActivityDTO> weekActivities)
	{
		return new WeekActivityOverviewDTO(date, weekActivities);
	}
}
