/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

/*
 * Offline activity for applications registered by the Yona app.
 * @see AnalysisEngineService
 * @see NetworkActivityDTO
 */
@JsonRootName("appActivity")
public class AppActivityDTO
{
	@JsonRootName("activity")
	public static class Activity
	{
		private String application;
		private final Date startTime;
		private final Date endTime;

		@JsonCreator
		public Activity(@JsonProperty("application") String application, @JsonProperty("startTime") Date startTime,
				@JsonProperty("endTime") Date endTime)
		{
			this.application = application;
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public String getApplication()
		{
			return application;
		}

		public Date getStartTime()
		{
			return startTime;
		}

		public Date getEndTime()
		{
			return endTime;
		}
	}

	private final ZonedDateTime deviceDateTime;
	private final Activity[] activities;

	@JsonCreator
	public AppActivityDTO(
			@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ") @JsonProperty("deviceDateTime") ZonedDateTime deviceDateTime,
			@JsonProperty("activities") Activity[] activities)
	{
		this.deviceDateTime = deviceDateTime;
		this.activities = activities;
	}

	public ZonedDateTime getDeviceDateTime()
	{
		return deviceDateTime;
	}

	public Activity[] getActivities()
	{
		return activities;
	}
}
