package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;

@JsonRootName("dayActivityOverview")
public class DayActivityOverviewDTO extends IntervalActivityOverviewDTO
{
	private Set<DayActivityDTO> dayActivities;

	private DayActivityOverviewDTO(LocalDate date, Set<DayActivityDTO> dayActivities)
	{
		super(date);
		this.dayActivities = dayActivities;
	}

	@Override
	public DateTimeFormatter getDateFormatter()
	{
		return DayActivityDTO.ISO8601_DAY_FORMATTER;
	}

	@JsonIgnore
	public Set<DayActivityDTO> getDayActivities()
	{
		return dayActivities;
	}

	static DayActivityOverviewDTO createInstance(LocalDate date, Set<DayActivity> dayActivities)
	{
		return new DayActivityOverviewDTO(date,
				dayActivities.stream().map(weekActivity -> DayActivityDTO.createInstance(weekActivity, LevelOfDetail.DayOverview))
						.collect(Collectors.toSet()));
	}
}
