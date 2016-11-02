package nu.yona.server.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
}
