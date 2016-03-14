package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.DayActivity;

@JsonRootName("dayActivity")
public class DayActivityDTO extends IntervalActivityDTO
{
	private final static String iso8601DayFormat = "yyyy-MM-dd";

	private DayActivityDTO(UUID goalID, ZonedDateTime startTime)
	{
		super(goalID, startTime);
	}

	@Override
	public String getDate()
	{
		return getStartTime().toLocalDate().format(DateTimeFormatter.ofPattern(iso8601DayFormat));
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, DateTimeFormatter.ofPattern(iso8601DayFormat));
	}

	static DayActivityDTO createInstance(DayActivity dayActivity)
	{
		return new DayActivityDTO(dayActivity.getGoal().getID(), dayActivity.getStartTime());
	}
}
