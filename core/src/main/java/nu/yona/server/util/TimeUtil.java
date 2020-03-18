/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class TimeUtil
{
	private TimeUtil()
	{
		// No instances
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
		return LocalDateTime.now();
	}

	public static LocalDateTime toUtcLocalDateTime(Date date)
	{
		return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
	}

	public static Date toDate(final LocalDateTime localDateTime)
	{
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	public static LocalDateTime toLocalDateTimeInZone(LocalDateTime utcDateTime, ZoneId zoneId)
	{
		return ZonedDateTime.ofInstant(utcDateTime.toInstant(java.time.ZoneOffset.UTC), zoneId).toLocalDateTime();
	}

	public static ZonedDateTime getStartOfDay(ZoneId zoneId, ZonedDateTime time)
	{
		return time.withZoneSameInstant(zoneId).truncatedTo(ChronoUnit.DAYS);
	}

	public static ZonedDateTime getEndOfDay(ZoneId zoneId, ZonedDateTime time)
	{
		return getStartOfDay(zoneId, time).plusDays(1);
	}

	public static ZonedDateTime getStartOfWeek(ZoneId zoneId, ZonedDateTime time)
	{
		LocalDate date = getStartOfDay(zoneId, time).toLocalDate();
		return getStartOfWeek(date).atStartOfDay(zoneId);
	}

	public static LocalDate getStartOfWeek(LocalDate date)
	{
		if (date.getDayOfWeek() == DayOfWeek.SUNDAY)
		{
			// take as the first day of week
			return date;
		}

		return date.minusDays(date.getDayOfWeek().getValue());
	}

	public static ZonedDateTime max(ZonedDateTime a, ZonedDateTime b)
	{
		return a.compareTo(b) > 0 ? a : b;
	}

	public static LocalDate max(LocalDate a, LocalDate b)
	{
		return a.compareTo(b) > 0 ? a : b;
	}
}
