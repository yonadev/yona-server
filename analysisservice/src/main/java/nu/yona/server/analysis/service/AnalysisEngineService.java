/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.TimeUtil;
import nu.yona.server.util.TransactionHelper;

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
	private ActivityCategoryService.FilterService activityCategoryFilterService;
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
	@Autowired
	private TransactionHelper transactionHelper;

	// This is intentionally not marked with @Transactional, as the transaction is explicitly started within the lock inside
	// analyze(ActivityPayload, Set<ActivityCategoryDto>)
	public void analyze(UUID userAnonymizedId, AppActivityDto appActivities)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		Duration deviceTimeOffset = determineDeviceTimeOffset(appActivities);
		for (AppActivityDto.Activity appActivity : appActivities.getActivities())
		{
			Set<ActivityCategoryDto> matchingActivityCategories = activityCategoryFilterService
					.getMatchingCategoriesForApp(appActivity.getApplication());
			analyze(createActivityPayload(deviceTimeOffset, appActivity, userAnonymized), matchingActivityCategories);
		}
	}

	// This is intentionally not marked with @Transactional, as the transaction is explicitly started within the lock inside
	// analyze(ActivityPayload, Set<ActivityCategoryDto>)
	public void analyze(UUID userAnonymizedId, NetworkActivityDto networkActivity)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		Set<ActivityCategoryDto> matchingActivityCategories = activityCategoryFilterService
				.getMatchingCategoriesForSmoothwallCategories(networkActivity.getCategories());
		analyze(ActivityPayload.createInstance(userAnonymized, networkActivity), matchingActivityCategories);
	}

	private Duration determineDeviceTimeOffset(AppActivityDto appActivities)
	{
		Duration offset = Duration.between(ZonedDateTime.now(), appActivities.getDeviceDateTime());
		return (offset.abs().compareTo(DEVICE_TIME_INACCURACY_MARGIN) > 0) ? offset : Duration.ZERO; // Ignore if less than 10
																										// seconds
	}

	private ActivityPayload createActivityPayload(Duration deviceTimeOffset, AppActivityDto.Activity appActivity,
			UserAnonymizedDto userAnonymized)
	{
		ZonedDateTime correctedStartTime = correctTime(deviceTimeOffset, appActivity.getStartTime());
		ZonedDateTime correctedEndTime = correctTime(deviceTimeOffset, appActivity.getEndTime());
		String application = appActivity.getApplication();
		assertValidTimes(userAnonymized, application, correctedStartTime, correctedEndTime);
		return ActivityPayload.createInstance(userAnonymized, correctedStartTime, correctedEndTime, application);
	}

	private void assertValidTimes(UserAnonymizedDto userAnonymized, String application, ZonedDateTime correctedStartTime,
			ZonedDateTime correctedEndTime)
	{
		if (correctedEndTime.isBefore(correctedStartTime))
		{
			throw AnalysisException.appActivityStartAfterEnd(userAnonymized.getId(), application, correctedStartTime,
					correctedEndTime);
		}
		if (correctedStartTime.isAfter(ZonedDateTime.now().plus(DEVICE_TIME_INACCURACY_MARGIN)))
		{
			throw AnalysisException.appActivityStartsInFuture(userAnonymized.getId(), application, correctedStartTime);
		}
		if (correctedEndTime.isAfter(ZonedDateTime.now().plus(DEVICE_TIME_INACCURACY_MARGIN)))
		{
			throw AnalysisException.appActivityEndsInFuture(userAnonymized.getId(), application, correctedEndTime);
		}
	}

	private ZonedDateTime correctTime(Duration deviceTimeOffset, ZonedDateTime time)
	{
		return time.minus(deviceTimeOffset);
	}

	private void analyze(ActivityPayload payload, Set<ActivityCategoryDto> matchingActivityCategories)
	{
		// We add a lock here because we further down in this class need to prevent conflicting updates to the DayActivity
		// entities.
		// The lock is added in this method (and not further down) so that we only have to lock once;
		// because the lock is per user, it doesn't matter much that we block early.
		try (LockPool<UUID>.Lock lock = userAnonymizedSynchronizer.lock(payload.userAnonymized.getId()))
		{
			transactionHelper.executeInNewTransaction(() -> {
				UserAnonymizedEntityHolder userAnonymizedHolder = new UserAnonymizedEntityHolder(payload.userAnonymized.getId());
				analyzeInsideLock(userAnonymizedHolder, payload, matchingActivityCategories);
				if (userAnonymizedHolder.isEntityFetched())
				{
					userAnonymizedService.updateUserAnonymized(userAnonymizedHolder.getEntity());
				}
			});
		}
	}

	private void analyzeInsideLock(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload,
			Set<ActivityCategoryDto> matchingActivityCategories)
	{
		updateLastMonitoredActivityDateIfRelevant(userAnonymizedHolder, payload);
		Set<GoalDto> matchingGoalsOfUser = determineMatchingGoalsForUser(payload.userAnonymized, matchingActivityCategories,
				payload.startTime);
		for (GoalDto matchingGoalOfUser : matchingGoalsOfUser)
		{
			addOrUpdateActivity(userAnonymizedHolder, payload, matchingGoalOfUser);
		}
	}

	private void updateLastMonitoredActivityDateIfRelevant(UserAnonymizedEntityHolder userAnonymizedHolder,
			ActivityPayload payload)
	{
		Optional<LocalDate> lastMonitoredActivityDate = payload.userAnonymized.getLastMonitoredActivityDate();
		LocalDate activityEndTime = payload.endTime.toLocalDate();
		if (lastMonitoredActivityDate.map(d -> d.isBefore(activityEndTime)).orElse(true))
		{
			userAnonymizedHolder.getEntity().setLastMonitoredActivityDate(activityEndTime);
		}
	}

	private void addOrUpdateActivity(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload,
			GoalDto matchingGoal)
	{
		if (isCrossDayActivity(payload))
		{
			// assumption: activity never crosses 2 days
			ActivityPayload truncatedPayload = ActivityPayload.copyTillEndTime(payload,
					TimeUtil.getEndOfDay(payload.userAnonymized.getTimeZone(), payload.startTime));
			ActivityPayload nextDayPayload = ActivityPayload.copyFromStartTime(payload,
					TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.endTime));

			addOrUpdateDayTruncatedActivity(userAnonymizedHolder, truncatedPayload, matchingGoal);
			addOrUpdateDayTruncatedActivity(userAnonymizedHolder, nextDayPayload, matchingGoal);
		}
		else
		{
			addOrUpdateDayTruncatedActivity(userAnonymizedHolder, payload, matchingGoal);
		}
	}

	private void addOrUpdateDayTruncatedActivity(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload,
			GoalDto matchingGoal)
	{
		ActivityDto lastRegisteredActivity = getLastRegisteredActivity(payload, matchingGoal);
		if (canCombineWithLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			if (isBeyondSkipWindowAfterLastRegisteredActivity(payload, lastRegisteredActivity))
			{
				// Update message only if it is within five seconds to avoid unnecessary cache flushes.
				updateActivityEndTime(userAnonymizedHolder, payload, matchingGoal, lastRegisteredActivity);
			}
		}
		else
		{
			addActivity(userAnonymizedHolder, payload, matchingGoal, lastRegisteredActivity);
		}
	}

	private boolean isBeyondSkipWindowAfterLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return Duration.between(lastRegisteredActivity.getEndTime(), payload.endTime)
				.compareTo(yonaProperties.getAnalysisService().getUpdateSkipWindow()) >= 0;
	}

	private boolean canCombineWithLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
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

	private boolean isOnNewDay(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.startTime)
				.isAfter(lastRegisteredActivity.getStartTime());
	}

	private boolean precedesLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return payload.startTime.isBefore(lastRegisteredActivity.getStartTime());
	}

	private boolean isBeyondCombineIntervalWithLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		ZonedDateTime intervalEndTime = lastRegisteredActivity.getEndTime()
				.plus(yonaProperties.getAnalysisService().getConflictInterval());
		return payload.startTime.isAfter(intervalEndTime);
	}

	private ActivityDto getLastRegisteredActivity(ActivityPayload payload, GoalDto matchingGoal)
	{
		return cacheService.fetchLastActivityForUser(payload.userAnonymized.getId(), matchingGoal.getGoalId());
	}

	private boolean isCrossDayActivity(ActivityPayload payload)
	{
		return TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.endTime).isAfter(payload.startTime);
	}

	private void addActivity(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload, GoalDto matchingGoal,
			ActivityDto lastRegisteredActivity)
	{
		Goal matchingGoalEntity = goalService.getGoalEntityForUserAnonymizedId(payload.userAnonymized.getId(),
				matchingGoal.getGoalId());
		Activity addedActivity = createNewActivity(userAnonymizedHolder.getEntity(), payload, matchingGoalEntity);
		if (shouldUpdateCache(lastRegisteredActivity, addedActivity))
		{
			cacheService.updateLastActivityForUser(payload.userAnonymized.getId(), matchingGoal.getGoalId(),
					ActivityDto.createInstance(addedActivity));
		}

		// Save first, so the activity is available when saving the message
		userAnonymizedService.updateUserAnonymized(userAnonymizedHolder.getEntity());
		if (matchingGoal.isNoGoGoal())
		{
			sendConflictMessageToAllDestinationsOfUser(userAnonymizedHolder.getEntity(), payload, addedActivity,
					matchingGoalEntity);
		}
	}

	private void updateActivityEndTime(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload,
			GoalDto matchingGoal, ActivityDto lastRegisteredActivity)
	{
		DayActivity dayActivity = findExistingDayActivity(payload, matchingGoal.getGoalId());
		if (dayActivity == null)
		{
			throw AnalysisException.dayActivityNotFound(payload.userAnonymized.getId(), matchingGoal.getGoalId(),
					payload.startTime, lastRegisteredActivity.getStartTime(), lastRegisteredActivity.getEndTime());
		}
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		Activity activity = dayActivity.getLastActivity();
		activity.setEndTime(payload.endTime.toLocalDateTime());
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		if (shouldUpdateCache(lastRegisteredActivity, activity))
		{
			cacheService.updateLastActivityForUser(payload.userAnonymized.getId(), matchingGoal.getGoalId(),
					ActivityDto.createInstance(activity));
		}

		// Explicitly fetch the entity to indicate that the user entity is dirty
		userAnonymizedHolder.getEntity();
	}

	private boolean shouldUpdateCache(ActivityDto lastRegisteredActivity, Activity newOrUpdatedActivity)
	{
		if (lastRegisteredActivity == null)
		{
			return true;
		}

		// do not update the cache if the new or updated activity occurs earlier than the last registered activity
		return !newOrUpdatedActivity.getEndTime().atZone(newOrUpdatedActivity.getTimeZone())
				.isBefore(lastRegisteredActivity.getEndTime());
	}

	private Activity createNewActivity(UserAnonymized userAnonymized, ActivityPayload payload, Goal matchingGoal)
	{
		DayActivity dayActivity = findExistingDayActivity(payload, matchingGoal.getId());
		if (dayActivity == null)
		{
			dayActivity = createNewDayActivity(userAnonymized, payload, matchingGoal);
		}

		ZonedDateTime endTime = ensureMinimumDurationOneMinute(payload);
		Activity activity = Activity.createInstance(payload.startTime.getZone(), payload.startTime.toLocalDateTime(),
				endTime.toLocalDateTime());
		dayActivity.addActivity(activity);
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		return dayActivity.getLastActivity();
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

	private DayActivity createNewDayActivity(UserAnonymized userAnonymizedEntity, ActivityPayload payload, Goal matchingGoal)
	{
		DayActivity dayActivity = DayActivity.createInstance(userAnonymizedEntity, matchingGoal, payload.startTime.getZone(),
				TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.startTime).toLocalDate());

		ZonedDateTime startOfWeek = TimeUtil.getStartOfWeek(payload.userAnonymized.getTimeZone(), payload.startTime);
		WeekActivity weekActivity = weekActivityRepository.findOne(payload.userAnonymized.getId(), matchingGoal.getId(),
				startOfWeek.toLocalDate());
		if (weekActivity == null)
		{
			weekActivity = WeekActivity.createInstance(userAnonymizedEntity, matchingGoal, startOfWeek.getZone(),
					startOfWeek.toLocalDate());
			matchingGoal.addWeekActivity(weekActivity);
		}
		weekActivity.addDayActivity(dayActivity);

		return dayActivity;
	}

	private DayActivity findExistingDayActivity(ActivityPayload payload, UUID matchingGoalId)
	{
		return dayActivityRepository.findOne(payload.userAnonymized.getId(),
				TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.startTime).toLocalDate(), matchingGoalId);
	}

	@Transactional
	public Set<String> getRelevantSmoothwallCategories()
	{
		return activityCategoryService.getAllActivityCategories().stream().flatMap(g -> g.getSmoothwallCategories().stream())
				.collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(UserAnonymized userAnonymized, ActivityPayload payload,
			Activity activity, Goal matchingGoal)
	{
		GoalConflictMessage selfGoalConflictMessage = GoalConflictMessage.createInstance(payload.userAnonymized.getId(), activity,
				matchingGoal, payload.url);
		messageService.sendMessage(selfGoalConflictMessage, userAnonymized.getAnonymousDestination());
		// Save the messages, so the other messages can reference it
		userAnonymizedService.updateUserAnonymized(userAnonymized);

		messageService.broadcastMessageToBuddies(payload.userAnonymized,
				() -> GoalConflictMessage.createInstanceForBuddy(payload.userAnonymized.getId(), selfGoalConflictMessage));
	}

	private Set<GoalDto> determineMatchingGoalsForUser(UserAnonymizedDto userAnonymized,
			Set<ActivityCategoryDto> matchingActivityCategories, ZonedDateTime activityStartTime)
	{
		Set<UUID> matchingActivityCategoryIds = matchingActivityCategories.stream().map(ActivityCategoryDto::getId)
				.collect(Collectors.toSet());
		Set<GoalDto> goalsOfUser = userAnonymized.getGoals();
		return goalsOfUser.stream().filter(g -> !g.isHistoryItem())
				.filter(g -> matchingActivityCategoryIds.contains(g.getActivityCategoryId()))
				.filter(g -> g.getCreationTime().get()
						.isBefore(TimeUtil.toUtcLocalDateTime(activityStartTime.plus(DEVICE_TIME_INACCURACY_MARGIN))))
				.collect(Collectors.toSet());
	}

	private static class ActivityPayload
	{
		public final UserAnonymizedDto userAnonymized;
		public final Optional<String> url;
		public final ZonedDateTime startTime;
		public final ZonedDateTime endTime;
		public final Optional<String> application;

		private ActivityPayload(UserAnonymizedDto userAnonymized, Optional<String> url, ZonedDateTime startTime,
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

		static ActivityPayload createInstance(UserAnonymizedDto userAnonymized, NetworkActivityDto networkActivity)
		{
			ZonedDateTime startTime = networkActivity.getEventTime().orElse(ZonedDateTime.now())
					.withZoneSameInstant(userAnonymized.getTimeZone());
			return new ActivityPayload(userAnonymized, Optional.of(networkActivity.getUrl()), startTime, startTime,
					Optional.empty());
		}

		static ActivityPayload createInstance(UserAnonymizedDto userAnonymized, ZonedDateTime startTime, ZonedDateTime endTime,
				String application)
		{
			ZoneId userTimeZone = userAnonymized.getTimeZone();
			return new ActivityPayload(userAnonymized, Optional.empty(), startTime.withZoneSameInstant(userTimeZone),
					endTime.withZoneSameInstant(userTimeZone), Optional.of(application));
		}
	}

	/**
	 * Holds a user anonymized entity, provided it was fetched. The purpose of this class is to keep track of whether the user
	 * anonymized entity was fetched from the database. If it was fetched, it was most likely done to update something in the
	 * large composite of the user anonymized entity. If that was done, the user anonymized entity is dirty and needs to be saved.
	 * <br/>
	 * Saving it implies that JPA saves any updates to that entity itself, but also to any of the entities in associations marked
	 * with CascadeType.ALL or CascadeType.PERSIST. Here in the analysis service, that applies to new or updated Activity,
	 * DayActivity and WeekActivity entities.<br/>
	 * The alternative to having this class would be to always fetch the user anonymized entity at the start of "analyze", but
	 * that would imply that we always make a round trip to the database, even in cases were our optimizations make that
	 * unnecessary.<br/>
	 * The "analyze" method will always save the user anonymized entity to its repository if it was fetched. JPA take care of
	 * preventing unnecessary update actions.
	 */
	private class UserAnonymizedEntityHolder
	{
		private final UUID id;
		private Optional<UserAnonymized> entity = Optional.empty();

		UserAnonymizedEntityHolder(UUID id)
		{
			this.id = id;
		}

		UserAnonymized getEntity()
		{
			if (!entity.isPresent())
			{
				entity = Optional.of(userAnonymizedService.getUserAnonymizedEntity(id));
			}
			return entity.get();
		}

		boolean isEntityFetched()
		{
			return entity.isPresent();
		}
	}
}
