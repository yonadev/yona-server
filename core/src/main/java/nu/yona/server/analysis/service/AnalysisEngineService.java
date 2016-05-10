/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

@Service
public class AnalysisEngineService
{
	private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private ActivityCategoryService activityCategoryService;
	@Autowired
	private AnalysisEngineCacheService cacheService;
	@Autowired
	private UserAnonymizedService userAnonymizedService;
	@Autowired
	private MessageService messageService;
	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;

	public void analyze(UUID userAnonymizedID, AppActivityDTO appActivities)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Duration deviceTimeOffset = determineDeviceTimeOffset(appActivities);
		for (AppActivityDTO.Activity appActivity : appActivities.getActivities())
		{
			Set<ActivityCategoryDTO> matchingActivityCategories = activityCategoryService
					.getMatchingCategoriesForApp(appActivity.getApplication());
			analyze(createActivityPayload(deviceTimeOffset, appActivity), userAnonymized, matchingActivityCategories);
		}
	}

	private Duration determineDeviceTimeOffset(AppActivityDTO appActivities)
	{
		Duration offset = Duration.between(ZonedDateTime.now(), appActivities.getDeviceDateTime());
		return (offset.abs().compareTo(Duration.ofSeconds(10)) > 0) ? offset : Duration.ZERO; // Ignore if less than 10 seconds
	}

	private ActivityPayload createActivityPayload(Duration deviceTimeOffset, AppActivityDTO.Activity appActivity)
	{
		ZonedDateTime correctedStartTime = correctTime(deviceTimeOffset, appActivity.getStartTime());
		ZonedDateTime correctedEndTime = correctTime(deviceTimeOffset, appActivity.getEndTime());
		return new ActivityPayload(correctedStartTime, correctedEndTime, appActivity.getApplication());
	}

	private ZonedDateTime correctTime(Duration deviceTimeOffset, ZonedDateTime time)
	{
		return time.minus(deviceTimeOffset);
	}

	public void analyze(NetworkActivityDTO networkActivity)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(networkActivity.getVPNLoginID());
		Set<ActivityCategoryDTO> matchingActivityCategories = activityCategoryService
				.getMatchingCategoriesForSmoothwallCategories(networkActivity.getCategories());
		analyze(new ActivityPayload(networkActivity), userAnonymized, matchingActivityCategories);
	}

	private void analyze(ActivityPayload payload, UserAnonymizedDTO userAnonymized,
			Set<ActivityCategoryDTO> matchingActivityCategories)
	{
		Set<Goal> matchingGoalsOfUser = determineMatchingGoalsForUser(userAnonymized, matchingActivityCategories);
		for (Goal matchingGoalOfUser : matchingGoalsOfUser)
		{
			addOrUpdateActivity(payload, userAnonymized, matchingGoalOfUser);
		}
	}

	private void addOrUpdateActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal)
	{
		if (isCrossDayActivity(payload, userAnonymized))
		{
			// assumption: activity never crosses 2 days
			ActivityPayload truncatedPayload = new ActivityPayload(payload.url, payload.startTime,
					getEndOfDay(payload.startTime, userAnonymized), payload.application);

			ActivityPayload nextDayPayload = new ActivityPayload(payload.url, getStartOfDay(payload.endTime, userAnonymized),
					payload.endTime, payload.application);

			addOrUpdateDayTruncatedActivity(truncatedPayload, userAnonymized, matchingGoal);
			addOrUpdateDayTruncatedActivity(nextDayPayload, userAnonymized, matchingGoal);
		}
		else
		{
			addOrUpdateDayTruncatedActivity(payload, userAnonymized, matchingGoal);
		}
	}

	private void addOrUpdateDayTruncatedActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal)
	{
		DayActivityCacheResult dayActivity = getRegisteredDayActivity(payload, userAnonymized, matchingGoal);
		Activity lastRegisteredActivity = dayActivity.content == null ? null : dayActivity.content.getLastActivity();
		if (canCombineWithLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			if (isBeyondSkipWindowAfterLastRegisteredActivity(payload, lastRegisteredActivity))
			{
				// Update message only if it is within five seconds to avoid unnecessary cache flushes.
				updateActivityEndTime(payload, userAnonymized, matchingGoal, dayActivity, lastRegisteredActivity);
			}
		}
		else
		{
			addActivity(payload, userAnonymized, matchingGoal, dayActivity);
		}
	}

	private boolean isBeyondSkipWindowAfterLastRegisteredActivity(ActivityPayload payload, Activity lastRegisteredActivity)
	{
		return Duration.between(lastRegisteredActivity.getEndTime(), payload.endTime).toMillis() >= yonaProperties
				.getAnalysisService().getUpdateSkipWindow();
	}

	private boolean canCombineWithLastRegisteredActivity(ActivityPayload payload, Activity lastRegisteredActivity)
	{
		if (lastRegisteredActivity == null)
		{
			return false;
		}

		if (precedesLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			// This can happen with app activity.
			// Do not try to combine, add separately.
			return false;
		}

		if (isBeyondCombineIntervalWithLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			return false;
		}

		return true;
	}

	private boolean precedesLastRegisteredActivity(ActivityPayload payload, Activity lastRegisteredActivity)
	{
		return payload.startTime.isBefore(lastRegisteredActivity.getStartTime());
	}

	private boolean isBeyondCombineIntervalWithLastRegisteredActivity(ActivityPayload payload, Activity lastRegisteredActivity)
	{
		ZonedDateTime intervalEndTime = lastRegisteredActivity.getEndTime()
				.plus(Duration.ofMillis(yonaProperties.getAnalysisService().getConflictInterval()));
		return payload.startTime.isAfter(intervalEndTime);
	}

	private DayActivityCacheResult getRegisteredDayActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized,
			Goal matchingGoal)
	{
		DayActivity lastRegisteredDayActivity = cacheService.fetchDayActivityForUser(userAnonymized.getID(),
				matchingGoal.getID());
		if (lastRegisteredDayActivity == null)
		{
			return DayActivityCacheResult.none();
		}
		else if (isOnPrecedingDay(payload, lastRegisteredDayActivity))
		{
			// Fetch from the database because it is no longer in the cache
			return DayActivityCacheResult.preceding(dayActivityRepository.findOne(userAnonymized.getID(),
					getStartOfDay(payload.startTime, userAnonymized).toLocalDate(), matchingGoal.getID()));
		}
		else if (isOnFollowingDay(payload, lastRegisteredDayActivity))
		{
			return DayActivityCacheResult.following();
		}
		else
		{
			return DayActivityCacheResult.fromCache(lastRegisteredDayActivity);
		}
	}

	private boolean isOnFollowingDay(ActivityPayload payload, DayActivity lastRegisteredDayActivity)
	{
		return payload.startTime.isAfter(lastRegisteredDayActivity.getEndTime());
	}

	private boolean isOnPrecedingDay(ActivityPayload payload, DayActivity lastRegisteredDayActivity)
	{
		return payload.startTime.isBefore(lastRegisteredDayActivity.getStartTime());
	}

	private boolean isCrossDayActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized)
	{
		return getStartOfDay(payload.startTime, userAnonymized).isBefore(getStartOfDay(payload.endTime, userAnonymized));
	}

	private void addActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal,
			DayActivityCacheResult dayActivity)
	{
		DayActivity updatedDayActivity = createNewActivity(dayActivity.content, payload, userAnonymized, matchingGoal);
		if (dayActivity.shouldUpdateCache())
		{
			cacheService.updateDayActivityForUser(updatedDayActivity);
		}
		else
		{
			dayActivityRepository.save(updatedDayActivity);
		}

		if (matchingGoal.isNoGoGoal())
		{
			sendConflictMessageToAllDestinationsOfUser(payload, userAnonymized, updatedDayActivity.getLastActivity(),
					matchingGoal);
		}
	}

	private void updateActivityEndTime(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal,
			DayActivityCacheResult dayActivity, Activity activity)
	{
		assert userAnonymized.getID().equals(dayActivity.content.getUserAnonymized().getID());
		assert matchingGoal.getID().equals(dayActivity.content.getGoal().getID());

		activity.setEndTime(payload.endTime);
		if (dayActivity.shouldUpdateCache())
		{
			cacheService.updateDayActivityForUser(dayActivity.content);
		}
		else
		{
			dayActivityRepository.save(dayActivity.content);
		}
	}

	private ZonedDateTime getStartOfDay(ZonedDateTime time, UserAnonymizedDTO userAnonymized)
	{
		return time.withZoneSameInstant(ZoneId.of(userAnonymized.getTimeZoneId())).truncatedTo(ChronoUnit.DAYS);
	}

	private ZonedDateTime getEndOfDay(ZonedDateTime time, UserAnonymizedDTO userAnonymized)
	{
		return getStartOfDay(time, userAnonymized).plusHours(23).plusMinutes(59).plusSeconds(59);
	}

	private ZonedDateTime getStartOfWeek(ZonedDateTime time, UserAnonymizedDTO userAnonymized)
	{
		ZonedDateTime startOfDay = getStartOfDay(time, userAnonymized);
		switch (startOfDay.getDayOfWeek())
		{
			case SUNDAY:
				// take as the first day of week
				return startOfDay;
			default:
				// MONDAY=1, etc.
				return startOfDay.minusDays(startOfDay.getDayOfWeek().getValue());
		}
	}

	private DayActivity createNewActivity(DayActivity dayActivity, ActivityPayload payload, UserAnonymizedDTO userAnonymized,
			Goal matchingGoal)
	{
		if (dayActivity == null)
		{
			dayActivity = createNewDayActivity(payload, userAnonymized, matchingGoal);
		}

		ZonedDateTime endTime = ensureMinimumDurationOneMinute(payload);
		Activity activity = Activity.createInstance(payload.startTime, endTime);
		dayActivity.addActivity(activity);
		return dayActivity;
	}

	private ZonedDateTime ensureMinimumDurationOneMinute(ActivityPayload payload)
	{
		Duration duration = Duration.between(payload.startTime, payload.endTime);
		if (duration.compareTo(ONE_MINUTE) < 0)
		{
			return payload.endTime.plus(ONE_MINUTE);
		}
		return payload.endTime;
	}

	private DayActivity createNewDayActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal)
	{
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(userAnonymized.getID());

		DayActivity dayActivity = DayActivity.createInstance(userAnonymizedEntity, matchingGoal,
				getStartOfDay(payload.startTime, userAnonymized));

		ZonedDateTime startOfWeek = getStartOfWeek(payload.startTime, userAnonymized);
		WeekActivity weekActivity = cacheService.fetchWeekActivityForUser(userAnonymized.getID(), matchingGoal.getID(),
				startOfWeek.toLocalDate());
		if (weekActivity == null)
		{
			weekActivity = WeekActivity.createInstance(userAnonymizedEntity, matchingGoal, startOfWeek);
		}
		weekActivity.addDayActivity(dayActivity);
		cacheService.updateWeekActivityForUser(weekActivity);

		return dayActivity;
	}

	public Set<String> getRelevantSmoothwallCategories()
	{
		return activityCategoryService.getAllActivityCategories().stream().flatMap(g -> g.getSmoothwallCategories().stream())
				.collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(ActivityPayload payload, UserAnonymizedDTO userAnonymized,
			Activity activity, Goal matchingGoal)
	{
		GoalConflictMessage selfGoalConflictMessage = GoalConflictMessage.createInstance(userAnonymized.getID(), activity,
				matchingGoal, payload.url);
		messageService.sendMessage(selfGoalConflictMessage, userAnonymized.getAnonymousDestination());

		messageService.broadcastMessageToBuddies(userAnonymized,
				() -> GoalConflictMessage.createInstanceFromBuddy(userAnonymized.getID(), selfGoalConflictMessage));
	}

	private Set<Goal> determineMatchingGoalsForUser(UserAnonymizedDTO userAnonymized,
			Set<ActivityCategoryDTO> matchingActivityCategories)
	{
		Set<UUID> matchingActivityCategoryIDs = matchingActivityCategories.stream().map(ac -> ac.getID())
				.collect(Collectors.toSet());
		Set<Goal> goalsOfUser = userAnonymized.getGoals();
		Set<Goal> matchingGoalsOfUser = goalsOfUser.stream()
				.filter(g -> matchingActivityCategoryIDs.contains(g.getActivityCategory().getID())).collect(Collectors.toSet());
		return matchingGoalsOfUser;
	}

	private enum DayActivityCacheResultStatus
	{
		CACHE_EMPTY, FROM_CACHE, FOLLOWING_LAST_CACHED, PRECEDING_LAST_CACHED
	}

	private static class DayActivityCacheResult
	{
		public final DayActivityCacheResultStatus status;
		public DayActivity content;

		public DayActivityCacheResult(DayActivity content, DayActivityCacheResultStatus status)
		{
			this.status = status;
			this.content = content;
		}

		public boolean shouldUpdateCache()
		{
			return this.status != DayActivityCacheResultStatus.PRECEDING_LAST_CACHED;
		}

		public static DayActivityCacheResult none()
		{
			return new DayActivityCacheResult(null, DayActivityCacheResultStatus.CACHE_EMPTY);
		}

		public static DayActivityCacheResult fromCache(DayActivity lastRegisteredDayActivity)
		{
			return new DayActivityCacheResult(lastRegisteredDayActivity, DayActivityCacheResultStatus.FROM_CACHE);
		}

		public static DayActivityCacheResult following()
		{
			return new DayActivityCacheResult(null, DayActivityCacheResultStatus.FROM_CACHE);
		}

		public static DayActivityCacheResult preceding(DayActivity resultFromDatabase)
		{
			return new DayActivityCacheResult(resultFromDatabase, DayActivityCacheResultStatus.PRECEDING_LAST_CACHED);
		}
	}

	private class ActivityPayload
	{
		public final String url;
		public final ZonedDateTime startTime;
		public final ZonedDateTime endTime;
		public final String application;

		public ActivityPayload(NetworkActivityDTO networkActivity)
		{
			this.url = networkActivity.getURL();
			this.startTime = ZonedDateTime.now(); // now
			this.endTime = this.startTime;
			this.application = null;
		}

		public ActivityPayload(ZonedDateTime startTime, ZonedDateTime endTime, String application)
		{
			this.url = null;
			this.startTime = startTime;
			this.endTime = endTime;
			this.application = application;
		}

		public ActivityPayload(String url, ZonedDateTime startTime, ZonedDateTime endTime, String application)
		{
			this.url = url;
			this.startTime = startTime;
			this.endTime = endTime;
			this.application = application;
		}
	}
}
