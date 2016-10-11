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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("dayActivityOverview")
public class DayActivityOverviewDTO<T> extends IntervalActivityOverviewDTO
{
	private final Set<T> dayActivities;

	private DayActivityOverviewDTO(ZonedDateTime date, Set<T> dayActivities)
	{
		super(date);
		this.dayActivities = dayActivities;
	}

	@Override
	protected String formatDateAsISO(LocalDate date)
	{
		return DayActivityDTO.formatDate(date);
	}

	@JsonIgnore
	public Set<T> getDayActivities()
	{
		return dayActivities;
	}

	static DayActivityOverviewDTO<DayActivityDTO> createInstanceForUser(ZonedDateTime date, Set<DayActivityDTO> dayActivities)
	{
		return new DayActivityOverviewDTO<DayActivityDTO>(date, dayActivities);
	}

	public static DayActivityOverviewDTO<DayActivityWithBuddiesDTO> createInstanceForUserWithBuddies(ZonedDateTime date,
			Set<DayActivityDTO> dayActivities)
	{
		Map<UUID, List<DayActivityDTO>> dayActivitiesByActivityCategoryID = dayActivities.stream()
				.collect(Collectors.groupingBy(da -> da.getActivityCategoryID(), Collectors.toList()));
		Set<DayActivityWithBuddiesDTO> dayActivityDTOs = dayActivitiesByActivityCategoryID.entrySet().stream()
				.map(e -> DayActivityWithBuddiesDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toSet());
		return new DayActivityOverviewDTO<>(date, dayActivityDTOs);
	}
}
