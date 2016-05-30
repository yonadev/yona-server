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
	private int[] goalSpreadCells;

	// Default constructor is required for JPA
	public TimeZoneGoal()
	{

	}

	private TimeZoneGoal(UUID id, ZonedDateTime creationTime, ActivityCategory activityCategory, String[] zones,
			int[] goalSpreadCells)
	{
		super(id, creationTime, activityCategory);

		this.zones = zones;
		this.goalSpreadCells = goalSpreadCells;
	}

	private TimeZoneGoal(UUID id, TimeZoneGoal originalGoal, ZonedDateTime endTime)
	{
		super(id, originalGoal, endTime);

		this.zones = originalGoal.zones;
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
		// TODO: zones should be parsed? maybe in spread format
		return true;
	}

	@Override
	public int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		// TODO compute from spread and allowed zones
		return 0;
	}

	public static TimeZoneGoal createInstance(ZonedDateTime creationTime, ActivityCategory activityCategory, String[] zones)
	{
		return new TimeZoneGoal(UUID.randomUUID(), creationTime, activityCategory, zones, calculateGoalSpreadCells(zones));
	}

	private static int[] calculateGoalSpreadCells(String[] zones)
	{
		Set<Integer> goalSpreadCells = new HashSet<>();
		DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("HH:mm").toFormatter();
		for (String zone : zones)
		{
			String[] zoneBeginEnd = zone.split("-");
			int beginIndex = calculateSpreadIndex(formatter, zoneBeginEnd[0]);
			int endIndex = calculateSpreadIndex(formatter, zoneBeginEnd[1]);
			addIndexes(goalSpreadCells, beginIndex, endIndex);
		}
		return goalSpreadCells.stream().mapToInt(i -> i.intValue()).sorted().toArray();
	}

	private static void addIndexes(Set<Integer> goalSpreadCells, int beginIndex, int endIndex)
	{
		for (int i = beginIndex; (i < endIndex); i++)
		{
			goalSpreadCells.add(i);
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

	public List<Integer> getGoalSpreadCells()
	{
		return Arrays.stream(goalSpreadCells).mapToObj(i -> new Integer(i)).collect(Collectors.toList());
	}
}
