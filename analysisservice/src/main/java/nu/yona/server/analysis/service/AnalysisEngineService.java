/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.exceptions.AnalysisException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.TimeUtil;

@Service
public class AnalysisEngineService
{
	private static final Duration DEVICE_TIME_INACCURACY_MARGIN = Duration.ofSeconds(10);
	private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private ActivityCategoryService activityCategoryService;
	@Autowired
	private ActivityCacheService cacheService;
	@Autowired
	private UserAnonymizedService userAnonymizedService;
	@Autowired
	private GoalService goalService;
	@Autowired
	private MessageService messageService;
	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;
	@Autowired(required = false)
	private WeekActivityRepository weekActivityRepository;
	@Autowired
	private LockPool<UUID> userAnonymizedSynchronizer;

	@Transactional
	public void analyze(UUID userAnonymizedID, AppActivityDTO appActivities)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Duration deviceTimeOffset = determineDeviceTimeOffset(appActivities);
		for (AppActivityDTO.Activity appActivity : appActivities.getActivities())
		{
			Set<ActivityCategoryDTO> matchingActivityCategories = activityCategoryService
					.getMatchingCategoriesForApp(appActivity.getApplication());
			analyze(createActivityPayload(deviceTimeOffset, appActivity, userAnonymized), matchingActivityCategories);
		}
	}

	@Transactional
	public void analyze(UUID userAnonymizedID, NetworkActivityDTO networkActivity)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Set<ActivityCategoryDTO> matchingActivityCategories = activityCategoryService
				.getMatchingCategoriesForSmoothwallCategories(networkActivity.getCategories());
		analyze(ActivityPayload.createInstance(userAnonymized, networkActivity), matchingActivityCategories);
	}

	private Duration determineDeviceTimeOffset(AppActivityDTO appActivities)
	{
		Duration offset = Duration.between(ZonedDateTime.now(), appActivities.getDeviceDateTime());
		return (offset.abs().compareTo(DEVICE_TIME_INACCURACY_MARGIN) > 0) ? offset : Duration.ZERO; // Ignore if less than 10
																										// seconds
	}

	private ActivityPayload createActivityPayload(Duration deviceTimeOffset, AppActivityDTO.Activity appActivity,
			UserAnonymizedDTO userAnonymized)
	{
		ZonedDateTime correctedStartTime = correctTime(deviceTimeOffset, appActivity.getStartTime());
		ZonedDateTime correctedEndTime = correctTime(deviceTimeOffset, appActivity.getEndTime());
		String application = appActivity.getApplication();
		validateTimes(userAnonymized, application, correctedStartTime, correctedEndTime);
		return ActivityPayload.createInstance(userAnonymized, correctedStartTime, correctedEndTime, application);
	}

	private void validateTimes(UserAnonymizedDTO userAnonymized, String application, ZonedDateTime correctedStartTime,
			ZonedDateTime correctedEndTime)
	{
		if (correctedEndTime.isBefore(correctedStartTime))
		{
			throw AnalysisException.appActivityStartAfterEnd(userAnonymized.getID(), application, correctedStartTime,
					correctedEndTime);
		}
		if (correctedStartTime.isAfter(ZonedDateTime.now().plus(DEVICE_TIME_INACCURACY_MARGIN)))
		{
			throw AnalysisException.appActivityStartsInFuture(userAnonymized.getID(), application, correctedStartTime);
		}
		if (correctedEndTime.isAfter(ZonedDateTime.now().plus(DEVICE_TIME_INACCURACY_MARGIN)))
		{
			throw AnalysisException.appActivityEndsInFuture(userAnonymized.getID(), application, correctedEndTime);
		}
	}

	private ZonedDateTime correctTime(Duration deviceTimeOffset, ZonedDateTime time)
	{
		return time.minus(deviceTimeOffset);
	}

	private void analyze(ActivityPayload payload, Set<ActivityCategoryDTO> matchingActivityCategories)
	{
		// We add a lock here because we further down in this class need to prevent conflicting updates to the DayActivity
		// entities.
		// The lock is added in this method (and not further down) so that we only have to lock once;
		// because the lock is per user, it doesn't matter much that we block early.
		try (LockPool<UUID>.Lock lock = userAnonymizedSynchronizer.lock(payload.userAnonymized.getID()))
		{
			analyzeInsideLock(payload, matchingActivityCategories);
		}
	}

	private void analyzeInsideLock(ActivityPayload payload, Set<ActivityCategoryDTO> matchingActivityCategories)
	{
		Set<GoalDTO> matchingGoalsOfUser = determineMatchingGoalsForUser(payload.userAnonymized, matchingActivityCategories,
				payload.startTime);
		for (GoalDTO matchingGoalOfUser : matchingGoalsOfUser)
		{
			addOrUpdateActivity(payload, matchingGoalOfUser);
		}
	}

	private void addOrUpdateActivity(ActivityPayload payload, GoalDTO matchingGoal)
	{
		if (isCrossDayActivity(payload))
		{
			// assumption: activity never crosses 2 days
			ActivityPayload truncatedPayload = ActivityPayload.copyTillEndTime(payload,
					getEndOfDay(payload.startTime, payload.userAnonymized));
			ActivityPayload nextDayPayload = ActivityPayload.copyFromStartTime(payload,
					getStartOfDay(payload.endTime, payload.userAnonymized));

			addOrUpdateDayTruncatedActivity(truncatedPayload, matchingGoal);
			addOrUpdateDayTruncatedActivity(nextDayPayload, matchingGoal);
		}
		else
		{
			addOrUpdateDayTruncatedActivity(payload, matchingGoal);
		}
	}

	private void addOrUpdateDayTruncatedActivity(ActivityPayload payload, GoalDTO matchingGoal)
	{
		ActivityDTO lastRegisteredActivity = getLastRegisteredActivity(payload, matchingGoal);
		if (canCombineWithLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			if (isBeyondSkipWindowAfterLastRegisteredActivity(payload, lastRegisteredActivity))
			{
				// Update message only if it is within five seconds to avoid unnecessary cache flushes.
				updateActivityEndTime(payload, matchingGoal, lastRegisteredActivity);
			}
		}
		else
		{
			addActivity(payload, matchingGoal, lastRegisteredActivity);
		}
	}

	private boolean isBeyondSkipWindowAfterLastRegisteredActivity(ActivityPayload payload, ActivityDTO lastRegisteredActivity)
	{
		return Duration.between(lastRegisteredActivity.getEndTime(), payload.endTime)
				.compareTo(yonaProperties.getAnalysisService().getUpdateSkipWindow()) >= 0;
	}

	private boolean canCombineWithLastRegisteredActivity(ActivityPayload payload, ActivityDTO lastRegisteredActivity)
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

		if (isOnNewDay(payload, lastRegisteredActivity))
		{
			return false;
		}

		return true;
	}

	private boolean isOnNewDay(ActivityPayload payload, ActivityDTO lastRegisteredActivity)
	{
		return getStartOfDay(payload.startTime, payload.userAnonymized).isAfter(lastRegisteredActivity.getStartTime());
	}

	private boolean precedesLastRegisteredActivity(ActivityPayload payload, ActivityDTO lastRegisteredActivity)
	{
		return payload.startTime.isBefore(lastRegisteredActivity.getStartTime());
	}

	private boolean isBeyondCombineIntervalWithLastRegisteredActivity(ActivityPayload payload, ActivityDTO lastRegisteredActivity)
	{
		ZonedDateTime intervalEndTime = lastRegisteredActivity.getEndTime()
				.plus(yonaProperties.getAnalysisService().getConflictInterval());
		return payload.startTime.isAfter(intervalEndTime);
	}

	private ActivityDTO getLastRegisteredActivity(ActivityPayload payload, GoalDTO matchingGoal)
	{
		return cacheService.fetchLastActivityForUser(payload.userAnonymized.getID(), matchingGoal.getID());
	}

	private boolean isCrossDayActivity(ActivityPayload payload)
	{
		return getStartOfDay(payload.endTime, payload.userAnonymized).isAfter(payload.startTime);
	}

	private void addActivity(ActivityPayload payload, GoalDTO matchingGoal, ActivityDTO lastRegisteredActivity)
	{
		Goal matchingGoalEntity = goalService.getGoalEntityForUserAnonymizedID(payload.userAnonymized.getID(),
				matchingGoal.getID());
		Activity addedActivity = createNewActivity(payload, matchingGoalEntity);
		if (shouldUpdateCache(lastRegisteredActivity, addedActivity))
		{
			cacheService.updateLastActivityForUser(payload.userAnonymized.getID(), matchingGoal.getID(),
					ActivityDTO.createInstance(addedActivity));
		}

		if (matchingGoal.isNoGoGoal())
		{
			sendConflictMessageToAllDestinationsOfUser(payload, addedActivity, matchingGoalEntity);
		}
	}

	private void updateActivityEndTime(ActivityPayload payload, GoalDTO matchingGoal, ActivityDTO lastRegisteredActivity)
	{
		DayActivity dayActivity = findExistingDayActivity(payload, matchingGoal.getID());
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		Activity activity = dayActivity.getLastActivity();
		activity.setEndTime(payload.endTime.toLocalDateTime());
		DayActivity updatedDayActivity = dayActivityRepository.save(dayActivity);
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		Activity updatedActivity = updatedDayActivity.getLastActivity();
		if (shouldUpdateCache(lastRegisteredActivity, updatedActivity))
		{
			cacheService.updateLastActivityForUser(payload.userAnonymized.getID(), matchingGoal.getID(),
					ActivityDTO.createInstance(updatedActivity));
		}
	}

	private boolean shouldUpdateCache(ActivityDTO lastRegisteredActivity, Activity newOrUpdatedActivity)
	{
		if (lastRegisteredActivity == null)
			return true;

		// do not update the cache if the new or updated activity occurs earlier than the last registered activity
		return !newOrUpdatedActivity.getEndTime().atZone(newOrUpdatedActivity.getTimeZone())
				.isBefore(lastRegisteredActivity.getEndTime());
	}

	private ZonedDateTime getStartOfDay(ZonedDateTime time, UserAnonymizedDTO userAnonymized)
	{
		return time.withZoneSameInstant(userAnonymized.getTimeZone()).truncatedTo(ChronoUnit.DAYS);
	}

	private ZonedDateTime getEndOfDay(ZonedDateTime time, UserAnonymizedDTO userAnonymized)
	{
		return getStartOfDay(time, userAnonymized).withHour(23).withMinute(59).withSecond(59);
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

	private Activity createNewActivity(ActivityPayload payload, Goal matchingGoal)
	{
		DayActivity dayActivity = findExistingDayActivity(payload, matchingGoal.getID());
		if (dayActivity == null)
		{
			dayActivity = createNewDayActivity(payload, matchingGoal);
		}

		ZonedDateTime endTime = ensureMinimumDurationOneMinute(payload);
		Activity activity = Activity.createInstance(payload.startTime.getZone(), payload.startTime.toLocalDateTime(),
				endTime.toLocalDateTime());
		dayActivity.addActivity(activity);
		DayActivity updatedDayActivity = dayActivityRepository.save(dayActivity);
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		return updatedDayActivity.getLastActivity();
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

	private DayActivity createNewDayActivity(ActivityPayload payload, Goal matchingGoal)
	{
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(payload.userAnonymized.getID());

		DayActivity dayActivity = DayActivity.createInstance(userAnonymizedEntity, matchingGoal, payload.startTime.getZone(),
				getStartOfDay(payload.startTime, payload.userAnonymized).toLocalDate());

		ZonedDateTime startOfWeek = getStartOfWeek(payload.startTime, payload.userAnonymized);
		WeekActivity weekActivity = weekActivityRepository.findOne(payload.userAnonymized.getID(), matchingGoal.getID(),
				startOfWeek.toLocalDate());
		if (weekActivity == null)
		{
			weekActivity = WeekActivity.createInstance(userAnonymizedEntity, matchingGoal, startOfWeek.getZone(),
					startOfWeek.toLocalDate());
		}
		dayActivityRepository.save(dayActivity);
		weekActivityRepository.save(weekActivity);

		return dayActivity;
	}

	private DayActivity findExistingDayActivity(ActivityPayload payload, UUID matchingGoalID)
	{
		return dayActivityRepository.findOne(payload.userAnonymized.getID(),
				getStartOfDay(payload.startTime, payload.userAnonymized).toLocalDate(), matchingGoalID);
	}

	@Transactional
	public Set<String> getRelevantSmoothwallCategories()
	{
		return activityCategoryService.getAllActivityCategories().stream().flatMap(g -> g.getSmoothwallCategories().stream())
				.collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(ActivityPayload payload, Activity activity, Goal matchingGoal)
	{
		GoalConflictMessage selfGoalConflictMessage = GoalConflictMessage.createInstance(payload.userAnonymized.getID(), activity,
				matchingGoal, payload.url);
		messageService.sendMessage(selfGoalConflictMessage, payload.userAnonymized.getAnonymousDestination());

		messageService.broadcastMessageToBuddies(payload.userAnonymized,
				() -> GoalConflictMessage.createInstanceFromBuddy(payload.userAnonymized.getID(), selfGoalConflictMessage));
	}

	private Set<GoalDTO> determineMatchingGoalsForUser(UserAnonymizedDTO userAnonymized,
			Set<ActivityCategoryDTO> matchingActivityCategories, ZonedDateTime activityStartTime)
	{
		Set<UUID> matchingActivityCategoryIDs = matchingActivityCategories.stream().map(ac -> ac.getID())
				.collect(Collectors.toSet());
		Set<GoalDTO> goalsOfUser = userAnonymized.getGoals();
		Set<GoalDTO> matchingGoalsOfUser = goalsOfUser.stream().filter(g -> !g.isHistoryItem())
				.filter(g -> matchingActivityCategoryIDs.contains(g.getActivityCategoryID()))
				.filter(g -> g.getCreationTime().get()
						.isBefore(TimeUtil.toUtcLocalDateTime(activityStartTime.plus(DEVICE_TIME_INACCURACY_MARGIN))))
				.collect(Collectors.toSet());
		return matchingGoalsOfUser;
	}

	private static class ActivityPayload
	{
		public final UserAnonymizedDTO userAnonymized;
		public final Optional<String> url;
		public final ZonedDateTime startTime;
		public final ZonedDateTime endTime;
		public final Optional<String> application;

		private ActivityPayload(UserAnonymizedDTO userAnonymized, Optional<String> url, ZonedDateTime startTime,
				ZonedDateTime endTime, Optional<String> application)
		{
			this.userAnonymized = userAnonymized;
			this.url = url;
			this.startTime = startTime;
			this.endTime = endTime;
			this.application = application;
		}

		static ActivityPayload copyTillEndTime(ActivityPayload payload, ZonedDateTime endTime)
		{
			return new ActivityPayload(payload.userAnonymized, payload.url, payload.startTime, endTime, payload.application);
		}

		static ActivityPayload copyFromStartTime(ActivityPayload payload, ZonedDateTime startTime)
		{
			return new ActivityPayload(payload.userAnonymized, payload.url, startTime, payload.endTime, payload.application);
		}

		static ActivityPayload createInstance(UserAnonymizedDTO userAnonymized, NetworkActivityDTO networkActivity)
		{
			ZonedDateTime startTime = networkActivity.getEventTime().orElse(ZonedDateTime.now())
					.withZoneSameInstant(userAnonymized.getTimeZone());
			return new ActivityPayload(userAnonymized, Optional.of(networkActivity.getURL()), startTime, startTime,
					Optional.empty());
		}

		static ActivityPayload createInstance(UserAnonymizedDTO userAnonymized, ZonedDateTime startTime, ZonedDateTime endTime,
				String application)
		{
			ZoneId userTimeZone = userAnonymized.getTimeZone();
			return new ActivityPayload(userAnonymized, Optional.empty(), startTime.withZoneSameInstant(userTimeZone),
					endTime.withZoneSameInstant(userTimeZone), Optional.of(application));
		}
	}
}
