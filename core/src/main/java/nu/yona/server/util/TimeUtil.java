package nu.yona.server.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeUtil
{
	private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

	private TimeUtil()
	{
		// No instances;
	}

	public static LocalDateTime toUtcLocalDateTime(ZonedDateTime zonedDateTime)
	{
		return zonedDateTime.withZoneSameInstant(UTC_ZONE_ID).toLocalDateTime();
	}

	public static ZonedDateTime toUtcZonedDateTime(LocalDateTime localDateTime)
	{
		return localDateTime.atZone(UTC_ZONE_ID);
	}

	public static LocalDateTime utcNow()
	{
		return LocalDateTime.now(UTC_ZONE_ID);
	}
}
