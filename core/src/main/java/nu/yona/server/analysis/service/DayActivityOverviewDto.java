/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("dayActivityOverview")
public class DayActivityOverviewDto<T> extends IntervalActivityOverviewDto
{
	private final Set<T> dayActivities;

	private DayActivityOverviewDto(ZonedDateTime date, Set<T> dayActivities)
	{
		super(date);
		this.dayActivities = dayActivities;
	}

	@Override
	protected String formatDateAsIso(LocalDate date)
	{
		return DayActivityDto.formatDate(date);
	}

	@JsonIgnore
	public Set<T> getDayActivities()
	{
		return dayActivities;
	}

	static DayActivityOverviewDto<DayActivityDto> createInstanceForUser(ZonedDateTime date, Set<DayActivityDto> dayActivities)
	{
		return new DayActivityOverviewDto<>(date, dayActivities);
	}

	public static DayActivityOverviewDto<DayActivityWithBuddiesDto> createInstanceForUserWithBuddies(ZonedDateTime date,
			Set<DayActivityDto> dayActivities)
	{
		Map<UUID, List<DayActivityDto>> dayActivitiesByActivityCategoryId = dayActivities.stream()
				.collect(Collectors.groupingBy(DayActivityDto::getActivityCategoryId, Collectors.toList()));
		Set<DayActivityWithBuddiesDto> dayActivityDtos = dayActivitiesByActivityCategoryId.entrySet().stream()
				.map(e -> DayActivityWithBuddiesDto.createInstance(e.getKey(), e.getValue())).collect(Collectors.toSet());
		return new DayActivityOverviewDto<>(date, dayActivityDtos);
	}
}
