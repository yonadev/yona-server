/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;

class ActivityPayload
{
	public final UserAnonymizedDto userAnonymized;
	public final Optional<String> url;
	public final ZonedDateTime startTime;
	public final ZonedDateTime endTime;
	public final Optional<String> application;
	public final DeviceAnonymizedDto deviceAnonymized;
	public final Set<ActivityCategoryDto> activityCategories;

	private ActivityPayload(UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized, Optional<String> url,
			ZonedDateTime startTime, ZonedDateTime endTime, Optional<String> application,
			Set<ActivityCategoryDto> activityCategories)
	{
		this.userAnonymized = userAnonymized;
		this.deviceAnonymized = deviceAnonymized;
		this.url = url;
		this.startTime = startTime;
		this.endTime = endTime;
		this.application = application;
		this.activityCategories = activityCategories;
	}

	boolean isNetworkActivity()
	{
		return !application.isPresent();
	}

	static ActivityPayload copyTillEndTime(ActivityPayload payload, ZonedDateTime endTime)
	{
		return new ActivityPayload(payload.userAnonymized, payload.deviceAnonymized, payload.url, payload.startTime, endTime,
				payload.application, payload.activityCategories);
	}

	static ActivityPayload copyFromStartTime(ActivityPayload payload, ZonedDateTime startTime)
	{
		return new ActivityPayload(payload.userAnonymized, payload.deviceAnonymized, payload.url, startTime, payload.endTime,
				payload.application, payload.activityCategories);
	}

	static ActivityPayload createInstance(UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized,
			NetworkActivityDto networkActivity, Set<ActivityCategoryDto> activityCategories)
	{
		ZonedDateTime startTime = networkActivity.getEventTime().orElse(ZonedDateTime.now())
				.withZoneSameInstant(userAnonymized.getTimeZone());
		return new ActivityPayload(userAnonymized, deviceAnonymized, Optional.of(networkActivity.getUrl()), startTime, startTime,
				Optional.empty(), activityCategories);
	}

	static ActivityPayload createInstance(UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized,
			ZonedDateTime startTime, ZonedDateTime endTime, String application, Set<ActivityCategoryDto> activityCategories)
	{
		ZoneId userTimeZone = userAnonymized.getTimeZone();
		return new ActivityPayload(userAnonymized, deviceAnonymized, Optional.empty(),
				startTime.withZoneSameInstant(userTimeZone), endTime.withZoneSameInstant(userTimeZone), Optional.of(application),
				activityCategories);
	}

	@Override
	public boolean equals(Object other)
	{
		if (other == this)
		{
			return true;
		}
		if (!(other instanceof ActivityPayload))
		{
			return false;
		}
		ActivityPayload otherActivityPayload = (ActivityPayload) other;
		return otherActivityPayload.application.equals(this.application) && otherActivityPayload.url.equals(this.url)
				&& otherActivityPayload.startTime.equals(this.startTime) && otherActivityPayload.endTime.equals(this.endTime)
				&& otherActivityPayload.deviceAnonymized.equals(this.deviceAnonymized)
				&& otherActivityPayload.userAnonymized.equals(this.userAnonymized);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(application, url, startTime, endTime, deviceAnonymized, userAnonymized);
	}
}