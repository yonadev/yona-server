/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.util.UUID;

import javax.persistence.Entity;

@Entity
public class TimeZoneGoal extends Goal
{
	private String[] zones;

	// Default constructor is required for JPA
	public TimeZoneGoal()
	{

	}

	private TimeZoneGoal(UUID id, ActivityCategory activityCategory, String[] zones)
	{
		super(id, activityCategory);

		this.zones = zones;
	}

	public String[] getZones()
	{
		return zones;
	}

	public static TimeZoneGoal createInstance(ActivityCategory activityCategory, String[] zones)
	{
		return new TimeZoneGoal(UUID.randomUUID(), activityCategory, zones);
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
}
