/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.BatchSize;

import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.ZoneIdAttributeConverter;
import nu.yona.server.goals.entities.ActivityCategory;

@Entity
@Table(name = "ACTIVITIES")
@BatchSize(size = 100)
public class Activity extends EntityWithId
{
	@Convert(converter = ZoneIdAttributeConverter.class)
	private ZoneId timeZone;
	private LocalDateTime startTime;
	private LocalDateTime endTime;
	private String app;

	@ManyToOne
	private DayActivity dayActivity;

	@ManyToOne
	private ActivityCategory activityCategory;

	// Default constructor is required for JPA
	public Activity()
	{
	}

	public Activity(ZoneId timeZone, LocalDateTime startTime, LocalDateTime endTime, Optional<String> app)
	{
		this.timeZone = timeZone;
		this.startTime = startTime;
		this.endTime = endTime;
		this.app = app.orElse(null);
	}

	public static Activity createInstance(ZoneId timeZone, LocalDateTime startTime, LocalDateTime endTime, Optional<String> app)
	{
		return new Activity(timeZone, startTime, endTime, app);
	}

	public DayActivity getDayActivity()
	{
		return dayActivity;
	}

	public void setDayActivity(DayActivity dayActivity)
	{
		this.dayActivity = dayActivity;
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

		dayActivity.resetAggregatesComputed();
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

		dayActivity.resetAggregatesComputed();
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

	public Optional<String> getApp()
	{
		return Optional.ofNullable(app);
	}
}
