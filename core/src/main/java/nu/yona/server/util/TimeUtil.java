package nu.yona.server.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class TimeUtil
{
	private TimeUtil()
	{
		// No instances;
	}

	public static LocalDateTime toUtcLocalDateTime(ZonedDateTime zonedDateTime)
	{
		return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	public static ZonedDateTime toUtcZonedDateTime(LocalDateTime localDateTime)
	{
		return localDateTime.atZone(ZoneOffset.UTC);
	}

	public static LocalDateTime utcNow()
	{
		return LocalDateTime.now(ZoneOffset.UTC);
	}

	public static LocalDateTime toUtcLocalDateTime(Date date)
	{
		return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
	}

	public static Date toDate(final LocalDateTime localDateTime)
	{
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	public static ZonedDateTime getStartOfDay(ZoneId zoneId, ZonedDateTime time)
	{
		return time.withZoneSameInstant(zoneId).truncatedTo(ChronoUnit.DAYS);
	}

	public static ZonedDateTime getEndOfDay(ZoneId zoneId, ZonedDateTime time)
	{
		return getStartOfDay(zoneId, time).withHour(23).withMinute(59).withSecond(59);
	}

	public static ZonedDateTime getStartOfWeek(ZoneId zoneId, ZonedDateTime time)
	{
		ZonedDateTime startOfDay = getStartOfDay(zoneId, time);
		switch (startOfDay.getDayOfWeek())
		{
			case SUNDAY:
				// take as the first day of week
				return startOfDay;
			default:
				// MONDAY=1, etc.
				return startOfDay.minusDays(startOfDay.getDayOfWeek().getValue());
		}
	}

}
