/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;

/*
 * Offline activity for applications registered by the Yona app.
 * @see AnalysisEngineService
 * @see NetworkActivityDto
 */
@JsonRootName("appActivity")
public class AppActivitiesDto
{
	@JsonRootName("activity")
	public static class Activity
	{
		private final String application;
		private final ZonedDateTime startTime;
		private final ZonedDateTime endTime;

		@JsonCreator
		public Activity(@JsonProperty("application") String application,
				@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("startTime") ZonedDateTime startTime,
				@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("endTime") ZonedDateTime endTime)
		{
			this.application = application;
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public String getApplication()
		{
			return application;
		}

		@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
		public ZonedDateTime getStartTime()
		{
			return startTime;
		}

		@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
		public ZonedDateTime getEndTime()
		{
			return endTime;
		}
	}

	private final ZonedDateTime deviceDateTime;
	private final Activity[] activities;

	@JsonCreator
	public AppActivitiesDto(
			@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty(value = "deviceDateTime", required = true) ZonedDateTime deviceDateTime,
			@JsonProperty(value = "activities", required = true) Activity[] activities)
	{
		this.deviceDateTime = deviceDateTime;
		this.activities = activities;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public ZonedDateTime getDeviceDateTime()
	{
		return deviceDateTime;
	}

	public Activity[] getActivities()
	{
		return activities;
	}

	@JsonIgnore
	public List<Activity> getActivitiesSorted()
	{
		return Arrays.stream(activities).sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime())).toList();
	}
}
