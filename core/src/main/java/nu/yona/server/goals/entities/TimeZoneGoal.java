/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.ZonedDateTime;
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

import javax.persistence.Entity;

import nu.yona.server.analysis.entities.DayActivity;

@Entity
public class TimeZoneGoal extends Goal
{
	private String[] zones;
	private int[] spreadCells;

	// Default constructor is required for JPA
	public TimeZoneGoal()
	{

	}

	private TimeZoneGoal(UUID id, ZonedDateTime creationTime, ActivityCategory activityCategory, String[] zones,
			int[] spreadCells)
	{
		super(id, creationTime, activityCategory);

		this.zones = zones;
		this.spreadCells = spreadCells;
	}

	private TimeZoneGoal(UUID id, TimeZoneGoal originalGoal, ZonedDateTime endTime)
	{
		super(id, originalGoal, endTime);

		this.zones = originalGoal.zones;
		this.spreadCells = originalGoal.spreadCells;
	}

	public String[] getZones()
	{
		return zones;
	}

	public void setZones(String[] zones)
	{
		this.zones = zones;
	}

	@Override
	public Goal cloneAsHistoryItem(ZonedDateTime endTime)
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
		Arrays.stream(spreadCells).forEach(i -> spread[i] = 0);
		return spread;
	}

	@Override
	public int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		int[] spread = determineSpreadOutsideGoal(dayActivity);
		return Arrays.stream(spread).sum();
	}

	public static TimeZoneGoal createInstance(ZonedDateTime creationTime, ActivityCategory activityCategory, String[] zones)
	{
		return new TimeZoneGoal(UUID.randomUUID(), creationTime, activityCategory, zones, calculateSpreadCells(zones));
	}

	private static int[] calculateSpreadCells(String[] zones)
	{
		Set<Integer> spreadCells = new HashSet<>();
		DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("HH:mm").toFormatter();
		for (String zone : zones)
		{
			String[] zoneBeginEnd = zone.split("-");
			int beginIndex = calculateSpreadIndex(formatter, zoneBeginEnd[0]);
			int endIndex = calculateSpreadIndex(formatter, zoneBeginEnd[1]);
			addIndexes(spreadCells, beginIndex, endIndex);
		}
		return spreadCells.stream().mapToInt(i -> i.intValue()).sorted().toArray();
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

	private static TimeZoneGoal createInstance(TimeZoneGoal originalGoal, ZonedDateTime endTime)
	{
		return new TimeZoneGoal(UUID.randomUUID(), originalGoal, endTime);
	}

	public List<Integer> getSpreadCells()
	{
		return Arrays.stream(spreadCells).mapToObj(i -> new Integer(i)).collect(Collectors.toList());
	}
}
