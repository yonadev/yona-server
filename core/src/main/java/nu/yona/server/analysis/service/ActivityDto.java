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
import nu.yona.server.analysis.entities.Activity;

@JsonRootName("activity")
public class ActivityDto
{
	private final ZonedDateTime startTime;
	private final ZonedDateTime endTime;

	@JsonCreator
	public ActivityDto(@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("startTime") ZonedDateTime startTime,
			@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("endTime") ZonedDateTime endTime)
	{
		this.startTime = startTime;
		this.endTime = endTime;
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

	static ActivityDto createInstance(Activity activity)
	{
		return new ActivityDto(activity.getStartTimeAsZonedDateTime(), activity.getEndTimeAsZonedDateTime());
	}
}
