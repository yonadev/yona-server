package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.WeekActivity;

@JsonRootName("weekActivity")
public class WeekActivityDTO extends IntervalActivityDTO
{
	private final static String iso8601WeekFormat = "yyyy-'W'w";

	private WeekActivityDTO(UUID goalID, ZonedDateTime startTime)
	{
		super(goalID, startTime);
	}

	@Override
	public String getDate()
	{
		return getStartTime().toLocalDate().format(DateTimeFormatter.ofPattern(iso8601WeekFormat));
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, DateTimeFormatter.ofPattern(iso8601WeekFormat));
	}

	static WeekActivityDTO createInstance(WeekActivity weekActivity)
	{
		return new WeekActivityDTO(weekActivity.getGoal().getID(), weekActivity.getStartTime());
	}
}
