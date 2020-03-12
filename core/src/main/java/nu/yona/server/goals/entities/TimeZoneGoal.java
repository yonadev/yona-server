/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.persistence.Column;
import javax.persistence.Entity;

import nu.yona.server.analysis.entities.DayActivity;

@Entity
public class TimeZoneGoal extends Goal implements ITimezoneGoal
{
	private static final long serialVersionUID = -8166664564237587040L;

	@Column(length = 24 * 4 * 12) // 24 hours, 4 quarters of an hour, 12 characters (hh:mm-hh:mm,)
	private String zones;

	@Column(length = 24 * 4) // 24 hours, 4 quarters of an hour
	private byte[] spreadCells;

	// Default constructor is required for JPA
	public TimeZoneGoal()
	{

	}

	private TimeZoneGoal(UUID id, LocalDateTime creationTime, ActivityCategory activityCategory, List<String> zones,
			List<Integer> spreadCells)
	{
		super(id, creationTime, activityCategory);

		this.zones = listToString(zones);
		this.spreadCells = integerListToByteArray(spreadCells);
	}

	private TimeZoneGoal(UUID id, TimeZoneGoal originalGoal, LocalDateTime endTime)
	{
		super(id, originalGoal, endTime);

		this.zones = originalGoal.zones;
		this.spreadCells = Arrays.copyOf(originalGoal.spreadCells, originalGoal.spreadCells.length);
	}

	public static TimeZoneGoal createInstance(LocalDateTime creationTime, ActivityCategory activityCategory, List<String> zones)
	{
		return new TimeZoneGoal(UUID.randomUUID(), creationTime, activityCategory, zones, calculateSpreadCells(zones));
	}

	private static TimeZoneGoal createInstance(TimeZoneGoal originalGoal, LocalDateTime endTime)
	{
		return new TimeZoneGoal(UUID.randomUUID(), originalGoal, endTime);
	}

	public List<String> getZones()
	{
		return stringToList(zones);
	}

	public void setZones(List<String> zones)
	{
		this.zones = listToString(zones);
		this.spreadCells = integerListToByteArray(calculateSpreadCells(zones));
	}

	@Override
	public Goal cloneAsHistoryItem(LocalDateTime endTime)
	{
		return createInstance(this, endTime);
	}

	@Override public boolean isGoalAccomplished(DayActivity dayActivity)
	{
		return ITimezoneGoal.super.isGoalAccomplished(dayActivity);
	}

	@Override public int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		return ITimezoneGoal.super.computeTotalMinutesBeyondGoal(dayActivity);
	}

	@Override public byte[] getSpreadCells()
	{
		return spreadCells;
	}

	private static byte[] integerListToByteArray(List<Integer> spreadCells)
	{
		byte[] bytes = new byte[spreadCells.size()];
		for (int i = 0; (i < bytes.length); i++)
		{
			bytes[i] = spreadCells.get(i).byteValue();
		}
		return bytes;
	}

	private static List<Integer> calculateSpreadCells(List<String> zones)
	{
		Set<Integer> spreadCells = new HashSet<>();
		DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("HH:mm").toFormatter();
		for (String zone : zones)
		{
			String[] zoneBeginEnd = zone.split("-");
			int beginIndex = calculateSpreadIndex(formatter, zoneBeginEnd[0]);
			int endIndex = calculateSpreadIndex(formatter, zoneBeginEnd[1]);
			endIndex = (endIndex == 0) ? 24 * 4 : endIndex; // To handle 24:00
			addIndexes(spreadCells, beginIndex, endIndex);
		}
		return spreadCells.stream().sorted().collect(Collectors.toList());
	}

	private static void addIndexes(Set<Integer> spreadCells, int beginIndex, int endIndex)
	{
		for (int i = beginIndex; (i < endIndex); i++)
		{
			spreadCells.add(i);
		}
	}

	private static int calculateSpreadIndex(DateTimeFormatter formatter, String timeString)
	{
		TemporalAccessor begin = formatter.parse(timeString);
		return begin.get(ChronoField.MINUTE_OF_DAY) / 15;
	}

	public static String listToString(List<String> entityValue)
	{
		if (entityValue == null)
		{
			return null;
		}
		return String.join(",", entityValue);
	}

	private static List<String> stringToList(String databaseValue)
	{
		if (databaseValue == null)
		{
			return null;
		}
		return Arrays.asList(databaseValue.split(","));
	}
}
