package nu.yona.server.analysis.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.WeekActivity;

@JsonRootName("weekActivity")
public class WeekActivityDTO extends IntervalActivityDTO
{
	private static final DateTimeFormatter ISO8601_WEEK_FORMATTER = new DateTimeFormatterBuilder().appendPattern("YYYY-'W'w")
			.parseDefaulting(WeekFields.ISO.dayOfWeek(), DayOfWeek.SUNDAY.getValue()).toFormatter();

	private List<DayActivityDTO> dayActivities;

	private WeekActivityDTO(UUID goalID, ZonedDateTime startTime, List<Integer> spread, int totalActivityDurationMinutes,
			List<DayActivityDTO> dayActivities)
	{
		super(goalID, startTime, spread, totalActivityDurationMinutes);
		this.dayActivities = dayActivities;
	}

	@Override
	public String getDate()
	{
		return getStartTime().toLocalDate().format(ISO8601_WEEK_FORMATTER);
	}

	public List<DayActivityDTO> getDayActivities()
	{
		return dayActivities;
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, ISO8601_WEEK_FORMATTER);
	}

	static WeekActivityDTO createInstance(WeekActivity weekActivity)
	{
		return new WeekActivityDTO(weekActivity.getGoal().getID(), weekActivity.getStartTime(), weekActivity.getSpread(),
				weekActivity.getTotalActivityDurationMinutes(), weekActivity.getDayActivities().stream()
						.map(dayActivity -> DayActivityDTO.createInstance(dayActivity)).collect(Collectors.toList()));
	}
}
