/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.ZoneIdAttributeConverter;
import nu.yona.server.goals.entities.ActivityCategory;

@Entity
@Table(name = "ACTIVITIES")
public class Activity extends EntityWithID
{
	@Convert(converter = ZoneIdAttributeConverter.class)
	private ZoneId timeZone;
	private LocalDateTime startTime;
	private LocalDateTime endTime;
	@ManyToOne
	private ActivityCategory activityCategory;

	// Default constructor is required for JPA
	public Activity()
	{
		super(null);
	}

	public Activity(UUID id, ZoneId timeZone, LocalDateTime startTime, LocalDateTime endTime)
	{
		super(id);
		this.timeZone = timeZone;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public ZoneId getTimeZone()
	{
		return timeZone;
	}

	public LocalDateTime getStartTime()
	{
		return startTime;
	}

	public ZonedDateTime getStartTimeAsZonedDateTime()
	{
		return startTime.atZone(timeZone);
	}

	public void setStartTime(LocalDateTime startTime)
	{
		this.startTime = startTime;
	}

	public LocalDateTime getEndTime()
	{
		return endTime;
	}

	public ZonedDateTime getEndTimeAsZonedDateTime()
	{
		return endTime.atZone(timeZone);
	}

	public void setEndTime(LocalDateTime endTime)
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

	public static Activity createInstance(ZoneId timeZone, LocalDateTime startTime, LocalDateTime endTime)
	{
		return new Activity(UUID.randomUUID(), timeZone, startTime, endTime);
	}
}
