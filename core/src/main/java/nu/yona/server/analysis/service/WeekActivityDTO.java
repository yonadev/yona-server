package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.WeekActivity;

@JsonRootName("weekActivity")
public class WeekActivityDTO extends IntervalActivityDTO
{
	private final static String ISO8601_WEEK_FORMAT = "yyyy-'W'w";

	private WeekActivityDTO(UUID goalID, ZonedDateTime startTime, List<Integer> spread, int totalActivityDurationMinutes)
	{
		super(goalID, startTime, spread, totalActivityDurationMinutes);
	}

	@Override
	public String getDate()
	{
		return getStartTime().toLocalDate().format(DateTimeFormatter.ofPattern(ISO8601_WEEK_FORMAT));
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, DateTimeFormatter.ofPattern(ISO8601_WEEK_FORMAT));
	}

	static WeekActivityDTO createInstance(WeekActivity weekActivity)
	{
		return new WeekActivityDTO(weekActivity.getGoal().getID(), weekActivity.getStartTime(), weekActivity.getSpread(),
				weekActivity.getTotalActivityDurationMinutes());
	}
}
