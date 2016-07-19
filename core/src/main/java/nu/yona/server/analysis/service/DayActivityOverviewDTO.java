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

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;

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

	static DayActivityOverviewDTO<DayActivityDTO> createInstanceForUser(ZonedDateTime date, Set<DayActivity> dayActivities)
	{
		return new DayActivityOverviewDTO<DayActivityDTO>(date,
				dayActivities.stream().map(dayActivity -> DayActivityDTO.createInstance(dayActivity, LevelOfDetail.DayOverview))
						.collect(Collectors.toSet()));
	}

	public static DayActivityOverviewDTO<DayActivityWithBuddiesDTO> createInstanceForUserWithBuddies(ZonedDateTime date,
			Set<DayActivity> dayActivities)
	{
		Map<UUID, List<DayActivity>> dayActivitiesByActivityCategoryID = dayActivities.stream()
				.collect(Collectors.groupingBy(da -> da.getGoal().getActivityCategory().getID(), Collectors.toList()));
		Set<DayActivityWithBuddiesDTO> dayActivityDTOs = dayActivitiesByActivityCategoryID.entrySet().stream()
				.map(e -> DayActivityWithBuddiesDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toSet());
		return new DayActivityOverviewDTO<>(date, dayActivityDTOs);
	}
}
