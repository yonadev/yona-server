/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nu.yona.server.Constants;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.device.entities.DeviceAnonymized;

@JsonRootName("activity")
public class ActivityDto
{
	private final Optional<UUID> deviceAnonymizedId;
	private final ZonedDateTime startTime;
	private final ZonedDateTime endTime;
	private final Optional<String> app;

	private ActivityDto(Optional<UUID> deviceAnonymizedId, ZonedDateTime startTime, ZonedDateTime endTime, Optional<String> app)
	{
		this.deviceAnonymizedId = deviceAnonymizedId;
		this.startTime = startTime;
		this.endTime = endTime;
		this.app = app;
	}

	static ActivityDto createInstance(Activity activity)
	{
		return new ActivityDto(activity.getDeviceAnonymized().map(DeviceAnonymized::getId),
				activity.getStartTimeAsZonedDateTime(), activity.getEndTimeAsZonedDateTime(), activity.getApp());
	}

	@JsonIgnore
	public Optional<UUID> getDeviceAnonymizedId()
	{
		return deviceAnonymizedId;
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

	@JsonSerialize(using = EmptyOptionalAsEmptyStringSerializer.class)
	public Optional<String> getApp()
	{
		return app;
	}

	public static class EmptyOptionalAsEmptyStringSerializer extends JsonSerializer<Optional<String>>
	{

		@Override
		public void serialize(Optional<String> app, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
				throws IOException
		{
			jsonGenerator.writeObject(app.orElse(""));
		}
	}
}
