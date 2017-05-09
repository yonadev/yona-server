/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.Constants;

/*
 * Online activity detected by Smoothwall.
 * @see AnalysisEngineService
 * @see AppActivityDto
 */
@JsonRootName("networkActivity")
public class NetworkActivityDto
{
	private static final int MAX_SUPPORTED_URL_LENGTH = 2048;
	private final Set<String> categories;
	private final String url;
	private final Optional<ZonedDateTime> eventTime;

	@JsonCreator
	public NetworkActivityDto(
			@JsonProperty("categories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> categories,
			@JsonProperty("url") String url,
			@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("eventTime") Optional<ZonedDateTime> eventTime)
	{
		this.categories = categories;
		this.url = (url.length() > MAX_SUPPORTED_URL_LENGTH) ? url.substring(0, MAX_SUPPORTED_URL_LENGTH) : url;
		this.eventTime = eventTime;
	}

	public Set<String> getCategories()
	{
		return Collections.unmodifiableSet(categories);
	}

	public String getUrl()
	{
		return url;
	}

	public Optional<ZonedDateTime> getEventTime()
	{
		return eventTime;
	}
}
