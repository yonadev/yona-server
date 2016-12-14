/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;

import nu.yona.server.analysis.entities.DayActivity;

@Entity
public class TimeZoneGoal extends Goal
{
	@Column(length = 24 * 4 * 6)
	private String zones;

	@ElementCollection
	private List<Integer> spreadCells;

	// Default constructor is required for JPA
	public TimeZoneGoal()
	{

	}

	private TimeZoneGoal(UUID id, LocalDateTime creationTime, ActivityCategory activityCategory, List<String> zones,
			List<Integer> spreadCells)
	{
		super(id, creationTime, activityCategory);

		this.zones = listToString(zones);
		this.spreadCells = spreadCells;
	}

	private TimeZoneGoal(UUID id, TimeZoneGoal originalGoal, LocalDateTime endTime)
	{
		super(id, originalGoal, endTime);

		this.zones = originalGoal.zones;
		this.spreadCells = new ArrayList<>(originalGoal.spreadCells);
	}

	public List<String> getZones()
	{
		return stringToList(zones);
	}

	public void setZones(List<String> zones)
	{
		this.zones = listToString(zones);
		this.spreadCells = calculateSpreadCells(zones);
	}

	@Override
	public Goal cloneAsHistoryItem(LocalDateTime endTime)
	{
		return createInstance(this, endTime);
	}

	@Override
	public boolean isMandatory()
	{
		return false;
	}

	@Override
	public boolean isNoGoGoal()
	{
		return false;
	}

	@Override
	public boolean isGoalAccomplished(DayActivity dayActivity)
	{
		int[] spread = determineSpreadOutsideGoal(dayActivity);
		return !Arrays.stream(spread).anyMatch(i -> (i > 0));
	}

	private int[] determineSpreadOutsideGoal(DayActivity dayActivity)
	{
		int[] spread = dayActivity.getSpread().stream().mapToInt(i -> i.intValue()).toArray();
		spreadCells.stream().forEach(i -> spread[i] = 0);
		return spread;
	}

	@Override
	public int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		int[] spread = determineSpreadOutsideGoal(dayActivity);
		int sumOfSpreadOutsideGoal = Arrays.stream(spread).sum();
		// Due to rounding, the sum of the spread might be more than the total duration, so take the lowest of the two
		return Math.min(dayActivity.getTotalActivityDurationMinutes(), sumOfSpreadOutsideGoal);
	}

	public static TimeZoneGoal createInstance(LocalDateTime creationTime, ActivityCategory activityCategory, List<String> zones)
	{
		return new TimeZoneGoal(UUID.randomUUID(), creationTime, activityCategory, zones, calculateSpreadCells(zones));
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

	private static TimeZoneGoal createInstance(TimeZoneGoal originalGoal, LocalDateTime endTime)
	{
		return new TimeZoneGoal(UUID.randomUUID(), originalGoal, endTime);
	}

	public List<Integer> getSpreadCells()
	{
		return new ArrayList<>(spreadCells);
	}

	public static String listToString(List<String> entityValue)
	{
		if (entityValue == null)
		{
			return null;
		}
		return String.join(",", entityValue);
	}

	public static List<String> stringToList(String databaseValue)
	{
		if (databaseValue == null)
		{
			return null;
		}
		return Arrays.asList(databaseValue.split(","));
	}
}
