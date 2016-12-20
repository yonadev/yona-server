/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;

/*
 * Offline activity for applications registered by the Yona app.
 * @see AnalysisEngineService
 * @see NetworkActivityDto
 */
@JsonRootName("appActivity")
public class AppActivityDto
{
	@JsonRootName("activity")
	public static class Activity
	{
		private String application;
		private final ZonedDateTime startTime;
		private final ZonedDateTime endTime;

		@JsonCreator
		public Activity(@JsonProperty("application") String application,
				@JsonFormat(pattern = Constants.ISO_DATE_PATTERN) @JsonProperty("startTime") ZonedDateTime startTime,
				@JsonFormat(pattern = Constants.ISO_DATE_PATTERN) @JsonProperty("endTime") ZonedDateTime endTime)
		{
			this.application = application;
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public String getApplication()
		{
			return application;
		}

		@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
		public ZonedDateTime getStartTime()
		{
			return startTime;
		}

		@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
		public ZonedDateTime getEndTime()
		{
			return endTime;
		}
	}

	private final ZonedDateTime deviceDateTime;
	private final Activity[] activities;

	@JsonCreator
	public AppActivityDto(
			@JsonFormat(pattern = Constants.ISO_DATE_PATTERN) @JsonProperty("deviceDateTime") ZonedDateTime deviceDateTime,
			@JsonProperty("activities") Activity[] activities)
	{
		this.deviceDateTime = deviceDateTime;
		this.activities = activities;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public ZonedDateTime getDeviceDateTime()
	{
		return deviceDateTime;
	}

	public Activity[] getActivities()
	{
		return activities;
	}
}
