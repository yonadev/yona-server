package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.IntervalActivity;

@JsonRootName("intervalActivity")
public class IntervalActivityDTO
{
	private UUID goalID;
	private ActivityTimeInterval timeInterval;
	private ZonedDateTime startTime;

	private IntervalActivityDTO(UUID goalID, ActivityTimeInterval timeInterval, ZonedDateTime startTime)
	{
		this.goalID = goalID;
		this.timeInterval = timeInterval;
		this.startTime = startTime;
	}

	/*
	 * The ISO-8601 week or day date.
	 */
	public String getDate()
	{
		return timeInterval.toIso8601();
	}

	/*
	 * The time zone in which the interval was recorded.
	 */
	public ZoneId getZoneId()
	{
		return startTime.getZone();
	}

	/*
	 * As the goal is already in the user profile, just a reference suffices. Notice that a goal may be removed; in that case it
	 * can be retrieved by the link.
	 */
	@JsonIgnore
	public UUID getGoalID()
	{
		return goalID;
	}

	static IntervalActivityDTO createInstance(IntervalActivity intervalActivity)
	{
		return new IntervalActivityDTO(intervalActivity.getGoal().getID(),
				new ActivityTimeInterval(intervalActivity.getTimeUnit(), intervalActivity.getStartTime().toLocalDate()),
				intervalActivity.getStartTime());
	}

	/*
	 * A helper class to be able to point to a certain week or day.
	 */
	public static class ActivityTimeInterval
	{
		private final static String iso8601WeekFormat = "yyyy-'W'w";
		private final static String iso8601DayFormat = "yyyy-MM-dd";

		private ChronoUnit timeUnit;
		private LocalDate startTime;

		ActivityTimeInterval(ChronoUnit timeUnit, LocalDate startTime)
		{
			if (timeUnit != ChronoUnit.DAYS || timeUnit != ChronoUnit.WEEKS)
				throw new IllegalArgumentException("Invalid time unit");

			this.timeUnit = timeUnit;
			this.startTime = startTime;
		}

		public ChronoUnit getTimeUnit()
		{
			return timeUnit;
		}

		public LocalDate getStartTime()
		{
			return startTime;
		}

		public static ActivityTimeInterval fromIso8601(String iso8601)
		{
			Optional<LocalDate> startTimeIfDay = parseIfDay(iso8601);
			if (startTimeIfDay.isPresent())
				return new ActivityTimeInterval(ChronoUnit.DAYS, startTimeIfDay.get());
			Optional<LocalDate> startTimeIfWeek = parseIfWeek(iso8601);
			if (startTimeIfWeek.isPresent())
				return new ActivityTimeInterval(ChronoUnit.WEEKS, startTimeIfWeek.get());

			throw new IllegalArgumentException("Expected format " + iso8601DayFormat + " or " + iso8601WeekFormat);
		}

		private static Optional<LocalDate> parseIfWeek(String iso8601)
		{
			try
			{
				return Optional.of(LocalDate.parse(iso8601, DateTimeFormatter.ofPattern(iso8601WeekFormat)));
			}
			catch (DateTimeParseException e)
			{
				return Optional.empty();
			}
		}

		private static Optional<LocalDate> parseIfDay(String iso8601)
		{
			try
			{
				return Optional.of(LocalDate.parse(iso8601, DateTimeFormatter.ofPattern(iso8601DayFormat)));
			}
			catch (DateTimeParseException e)
			{
				return Optional.empty();
			}
		}

		public String toIso8601()
		{
			switch (this.timeUnit)
			{
				case DAYS:
					return this.startTime.toString();
				case WEEKS:
					return this.startTime.format(DateTimeFormatter.ofPattern("yyyy-'W'w"));
				default:
					throw new IllegalStateException("Invalid time unit");
			}
		}
	}
}
