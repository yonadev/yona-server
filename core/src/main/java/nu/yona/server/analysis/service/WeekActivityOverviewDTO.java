package nu.yona.server.analysis.service;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;

@JsonRootName("weekActivityOverview")
public class WeekActivityOverviewDTO
{
	private Set<WeekActivityDTO> weekActivities;

	private WeekActivityOverviewDTO(Set<WeekActivityDTO> weekActivities)
	{
		this.weekActivities = weekActivities;
	}

	@JsonIgnore
	public Set<WeekActivityDTO> getWeekActivities()
	{
		return weekActivities;
	}

	static WeekActivityOverviewDTO createInstance(Set<WeekActivity> weekActivities)
	{
		return new WeekActivityOverviewDTO(weekActivities.stream()
				.map(a -> WeekActivityDTO.createInstance(a, LevelOfDetail.WeekOverview)).collect(Collectors.toSet()));
	}
}
