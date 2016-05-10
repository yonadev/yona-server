/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.ActivityCategory;

@Entity
@Table(name = "ACTIVITIES")
public class Activity extends EntityWithID
{
	private ZonedDateTime startTime;
	private ZonedDateTime endTime;
	@ManyToOne
	private ActivityCategory activityCategory;

	// Default constructor is required for JPA
	public Activity()
	{
		super(null);
	}

	public Activity(UUID id, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		super(id);
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	public void setStartTime(ZonedDateTime startTime)
	{
		this.startTime = startTime;
	}

	public ZonedDateTime getEndTime()
	{
		return endTime;
	}

	public void setEndTime(ZonedDateTime endTime)
	{
		this.endTime = endTime;
	}

	public int getDurationMinutes()
	{
		return (int) Duration.between(getStartTime(), getEndTime()).toMinutes();
	}

	public void setActivityCategory(ActivityCategory activityCategory)
	{
		this.activityCategory = activityCategory;
	}

	public ActivityCategory getActivityCategory()
	{
		return activityCategory;
	}

	public static Activity createInstance(ZonedDateTime startTime, ZonedDateTime endTime)
	{
		return new Activity(UUID.randomUUID(), startTime, endTime);
	}
}
