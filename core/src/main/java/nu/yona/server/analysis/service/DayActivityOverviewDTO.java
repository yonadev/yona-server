package nu.yona.server.analysis.service;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;

@JsonRootName("dayActivityOverview")
public class DayActivityOverviewDTO
{
	private Set<DayActivityDTO> dayActivities;

	private DayActivityOverviewDTO(Set<DayActivityDTO> dayActivities)
	{
		this.dayActivities = dayActivities;
	}

	@JsonIgnore
	public Set<DayActivityDTO> getWeekActivities()
	{
		return dayActivities;
	}

	static DayActivityOverviewDTO createInstance(Set<DayActivity> dayActivities)
	{
		return new DayActivityOverviewDTO(
				dayActivities.stream().map(weekActivity -> DayActivityDTO.createInstance(weekActivity, LevelOfDetail.DayOverview))
						.collect(Collectors.toSet()));
	}
}
