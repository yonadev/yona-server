/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.client;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.util.TimeUtil;

@JsonRootName("pinResetConfirmationCodeSendRequest")
public class PinResetConfirmationCodeSendRequestDto
{
	private final UUID userId;
	private final LocalDateTime executionTime;
	private final String localeString;

	@JsonCreator
	public PinResetConfirmationCodeSendRequestDto(@JsonProperty("userId") UUID userId,
			@JsonFormat(pattern = Constants.ISO_DATE_PATTERN) @JsonProperty("executionTime") Date executionTime,
			@JsonProperty("localeString") String localeString)
	{
		this(userId, TimeUtil.toUtcLocalDateTime(executionTime), localeString);
	}

	public PinResetConfirmationCodeSendRequestDto(UUID userId, LocalDateTime executionTime)
	{
		this(userId, executionTime, LocaleContextHolder.getLocale().toLanguageTag());
	}

	private PinResetConfirmationCodeSendRequestDto(UUID userId, LocalDateTime executionTime, String localeString)
	{
		this.userId = userId;
		this.executionTime = executionTime;
		this.localeString = localeString;
	}

	public UUID getUserId()
	{
		return userId;
	}

	// Jackson fails on LocalDateTime, so use Date to serialize
	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	@JsonProperty("executionTime")
	public Date getExecutionTimeAsUtilDate()
	{
		return TimeUtil.toDate(executionTime);
	}

	@JsonIgnore
	public LocalDateTime getExecutionTime()
	{
		return executionTime;
	}

	public String getLocaleString()
	{
		return localeString;
	}
}
