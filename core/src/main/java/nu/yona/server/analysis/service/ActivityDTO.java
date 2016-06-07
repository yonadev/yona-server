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
public class ActivityDTO
{
	private ZonedDateTime startTime;
	private ZonedDateTime endTime;

	@JsonCreator
	public ActivityDTO(@JsonFormat(pattern = Constants.ISO_DATE_PATTERN) @JsonProperty("startTime") ZonedDateTime startTime,
			@JsonFormat(pattern = Constants.ISO_DATE_PATTERN) @JsonProperty("endTime") ZonedDateTime endTime)
	{
		this.startTime = startTime;
		this.endTime = endTime;
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

	static ActivityDTO createInstance(Activity activity)
	{
		return new ActivityDTO(activity.getStartTime(), activity.getEndTime());
	}
}
