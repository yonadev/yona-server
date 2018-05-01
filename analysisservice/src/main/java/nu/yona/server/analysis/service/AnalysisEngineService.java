/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.ActivityRepository;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.exceptions.AnalysisException;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalDto;
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
	private static final Logger logger = LoggerFactory.getLogger(AnalysisEngineService.class);

	private static final Duration DEVICE_TIME_INACCURACY_MARGIN = Duration.ofSeconds(10);

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
	private DeviceService deviceService;
	@Autowired(required = false)
	private ActivityRepository activityRepository;
	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;
	@Autowired
	private LockPool<UUID> userAnonymizedSynchronizer;
	@Autowired
	private TransactionHelper transactionHelper;
	@Autowired
	private ActivityUpdateService activityUpdateService;

	// This is intentionally not marked with @Transactional, as the transaction is explicitly started within the lock inside
	// analyze(ActivityPayload, Set<ActivityCategoryDto>)
	public void analyze(UUID userAnonymizedId, UUID deviceAnonymizedId, AppActivityDto appActivities)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		DeviceAnonymizedDto deviceAnonymized = deviceService.getDeviceAnonymized(userAnonymized, deviceAnonymizedId);
		Duration deviceTimeOffset = determineDeviceTimeOffset(appActivities);
		for (AppActivityDto.Activity appActivity : appActivities.getActivities())
		{
			Set<ActivityCategoryDto> matchingActivityCategories = activityCategoryFilterService
					.getMatchingCategoriesForApp(appActivity.getApplication());
			analyze(createActivityPayload(deviceTimeOffset, appActivity, userAnonymized, deviceAnonymized),
					matchingActivityCategories);
		}
	}

	// This is intentionally not marked with @Transactional, as the transaction is explicitly started within the lock inside
	// analyze(ActivityPayload, Set<ActivityCategoryDto>)
	public void analyze(UUID userAnonymizedId, NetworkActivityDto networkActivity)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		if (userAnonymized.getDevicesAnonymized().isEmpty())
		{
			// User did not open the app yet, so the migration step did not add the default device
			// Ignore the network activities till the user opened the app
			return;
		}
		Set<ActivityCategoryDto> matchingActivityCategories = activityCategoryFilterService
				.getMatchingCategoriesForSmoothwallCategories(networkActivity.getCategories());
		DeviceAnonymizedDto deviceAnonymized = deviceService.getDeviceAnonymized(userAnonymized,
				networkActivity.getDeviceIndex());
		analyze(ActivityPayload.createInstance(userAnonymized, deviceAnonymized, networkActivity), matchingActivityCategories);
	}

	private Duration determineDeviceTimeOffset(AppActivityDto appActivities)
	{
		Duration offset = Duration.between(ZonedDateTime.now(), appActivities.getDeviceDateTime());
		return (offset.abs().compareTo(DEVICE_TIME_INACCURACY_MARGIN) > 0) ? offset : Duration.ZERO; // Ignore if less than 10
																										// seconds
	}

	private ActivityPayload createActivityPayload(Duration deviceTimeOffset, AppActivityDto.Activity appActivity,
			UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized)
	{
		ZonedDateTime correctedStartTime = correctTime(deviceTimeOffset, appActivity.getStartTime());
		ZonedDateTime correctedEndTime = correctTime(deviceTimeOffset, appActivity.getEndTime());
		String application = appActivity.getApplication();
		assertValidTimes(userAnonymized, application, correctedStartTime, correctedEndTime);
		return ActivityPayload.createInstance(userAnonymized, deviceAnonymized, correctedStartTime, correctedEndTime,
				application);
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
		// The lock is added in this method (and not further down) so that we only have to lock once.
		// Because the lock is per user, it doesn't matter much that we block early.
		try (LockPool<UUID>.Lock lock = userAnonymizedSynchronizer.lock(payload.userAnonymized.getId()))
		{
			transactionHelper.executeInNewTransaction(() -> {
				UserAnonymizedEntityHolder userAnonymizedHolder = new UserAnonymizedEntityHolder(userAnonymizedService,
						payload.userAnonymized.getId());
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
		activityUpdateService.updateLastMonitoredActivityDateIfRelevant(userAnonymizedHolder, payload);

		Set<GoalDto> goals = determineRelevantGoals(payload, matchingActivityCategories);
		for (GoalDto goal : goals)
		{
			addOrUpdateActivity(userAnonymizedHolder, payload, goal);
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
		Optional<ActivityDto> lastRegisteredActivity = getLastRegisteredActivity(payload, matchingGoal);
		if (precedesLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			Optional<Activity> overlappingActivity = findOverlappingActivitySameApp(payload, matchingGoal);
			if (overlappingActivity.isPresent())
			{
				activityUpdateService.updateTimeExistingActivity(userAnonymizedHolder, payload, overlappingActivity.get());
				return;
			}
		}

		if (canCombineWithLastRegisteredActivity(payload, lastRegisteredActivity))
		{
			if (payload.startTime.isBefore(lastRegisteredActivity.get().getStartTime())
					|| isBeyondSkipWindowAfterLastRegisteredActivity(payload, lastRegisteredActivity.get()))
			{
				// Update message only if the start time is to be updated or if the end time moves with at least five seconds, to
				// avoid unnecessary cache flushes.
				activityUpdateService.updateTimeLastActivity(userAnonymizedHolder, payload, matchingGoal,
						lastRegisteredActivity.get());
			}
			return;
		}

		activityUpdateService.addActivity(userAnonymizedHolder, payload, matchingGoal, lastRegisteredActivity);
	}

	private Optional<Activity> findOverlappingActivitySameApp(ActivityPayload payload, GoalDto matchingGoal)
	{
		Optional<DayActivity> dayActivity = findExistingDayActivity(payload, matchingGoal.getGoalId());
		if (!dayActivity.isPresent())
		{
			return Optional.empty();
		}

		List<Activity> overlappingOfSameApp = activityRepository.findOverlappingOfSameApp(dayActivity.get(),
				payload.deviceAnonymized.getId(), matchingGoal.getActivityCategoryId(), payload.application.orElse(null),
				payload.startTime.toLocalDateTime(), payload.endTime.toLocalDateTime());

		// The prognosis is that there is no or one overlapping activity of the same app,
		// because we don't expect the mobile app to post app activity that spans other existing same app activity
		// (that indicates that there is something wrong in the app activity bookkeeping).
		// Network activity (having payload.application == null) also is not expected to exist and overlap, as network activity
		// has a very short time span.
		// So log if there are multiple overlaps.

		if (overlappingOfSameApp.isEmpty())
		{
			return Optional.empty();
		}
		if (overlappingOfSameApp.size() == 1)
		{
			return Optional.of(overlappingOfSameApp.get(0));
		}

		String overlappingActivitiesKind = payload.application.isPresent()
				? MessageFormat.format("app activities of ''{0}''", payload.application.get()) : "network activities";
		String overlappingActivities = overlappingOfSameApp.stream().map(Activity::toString).collect(Collectors.joining(", "));
		logger.warn(
				"Multiple overlapping {} found. The payload has start time {} and end time {}. The day activity ID is {} and the activity category ID is {}. The overlapping activities are: {}.",
				overlappingActivitiesKind, payload.startTime.toLocalDateTime(), payload.endTime.toLocalDateTime(),
				dayActivity.get().getId(), matchingGoal.getActivityCategoryId(), overlappingActivities);

		// Pick the first, we can't resolve this
		return Optional.of(overlappingOfSameApp.get(0));
	}

	private Optional<DayActivity> findExistingDayActivity(ActivityPayload payload, UUID matchingGoalId)
	{
		return Optional.ofNullable(dayActivityRepository.findOne(payload.userAnonymized.getId(),
				TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.startTime).toLocalDate(), matchingGoalId));
	}

	private boolean isBeyondSkipWindowAfterLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return Duration.between(lastRegisteredActivity.getEndTime(), payload.endTime)
				.compareTo(yonaProperties.getAnalysisService().getUpdateSkipWindow()) >= 0;
	}

	private boolean canCombineWithLastRegisteredActivity(ActivityPayload payload,
			Optional<ActivityDto> optionalLastRegisteredActivity)
	{
		if (!optionalLastRegisteredActivity.isPresent())
		{
			return false;
		}
		ActivityDto lastRegisteredActivity = optionalLastRegisteredActivity.get();

		if (precedesLastRegisteredActivity(payload, optionalLastRegisteredActivity))
		{
			// This can happen with app activity.
			// Handled separately
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

		if (isRelatedToDifferentApp(payload, lastRegisteredActivity))
		{
			return false;
		}

		if (isInterruptedAppActivity(payload, lastRegisteredActivity))
		{
			return false;
		}

		return true;
	}

	private boolean isInterruptedAppActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return payload.application.isPresent() && payload.startTime.isAfter(lastRegisteredActivity.getEndTime());
	}

	private boolean isRelatedToDifferentApp(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return !payload.application.equals(lastRegisteredActivity.getApp());
	}

	private boolean isOnNewDay(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		return TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.startTime)
				.isAfter(lastRegisteredActivity.getStartTime());
	}

	private boolean precedesLastRegisteredActivity(ActivityPayload payload, Optional<ActivityDto> lastRegisteredActivity)
	{
		return lastRegisteredActivity.map(lra -> payload.endTime.isBefore(lra.getStartTime())).orElse(false);
	}

	private boolean isBeyondCombineIntervalWithLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		ZonedDateTime intervalEndTime = lastRegisteredActivity.getEndTime()
				.plus(yonaProperties.getAnalysisService().getConflictInterval());
		return payload.startTime.isAfter(intervalEndTime);
	}

	private Optional<ActivityDto> getLastRegisteredActivity(ActivityPayload payload, GoalDto matchingGoal)
	{
		return Optional.ofNullable(cacheService.fetchLastActivityForUser(payload.userAnonymized.getId(),
				payload.deviceAnonymized.getId(), matchingGoal.getGoalId()));
	}

	private boolean isCrossDayActivity(ActivityPayload payload)
	{
		return TimeUtil.getEndOfDay(payload.userAnonymized.getTimeZone(), payload.startTime).isBefore(payload.endTime);
	}

	@Transactional
	public Set<String> getRelevantSmoothwallCategories()
	{
		return activityCategoryService.getAllActivityCategories().stream().flatMap(g -> g.getSmoothwallCategories().stream())
				.collect(Collectors.toSet());
	}

	static Set<GoalDto> determineRelevantGoals(ActivityPayload payload, Set<ActivityCategoryDto> matchingActivityCategories)
	{
		boolean onlyNoGoGoals = payload.isNetworkActivity()
				&& payload.deviceAnonymized.getOperatingSystem() == OperatingSystem.ANDROID;
		Set<UUID> matchingActivityCategoryIds = matchingActivityCategories.stream().map(ActivityCategoryDto::getId)
				.collect(Collectors.toSet());
		Set<GoalDto> goalsOfUser = payload.userAnonymized.getGoals();
		return goalsOfUser.stream().filter(g -> !g.isHistoryItem())
				.filter(g -> matchingActivityCategoryIds.contains(g.getActivityCategoryId()))
				.filter(g -> g.isNoGoGoal() || !onlyNoGoGoals)
				.filter(g -> g.getCreationTime().get()
						.isBefore(TimeUtil.toUtcLocalDateTime(payload.startTime.plus(DEVICE_TIME_INACCURACY_MARGIN))))
				.collect(Collectors.toSet());
	}

	static class ActivityPayload
	{
		public final UserAnonymizedDto userAnonymized;
		public final Optional<String> url;
		public final ZonedDateTime startTime;
		public final ZonedDateTime endTime;
		public final Optional<String> application;
		public final DeviceAnonymizedDto deviceAnonymized;

		private ActivityPayload(UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized, Optional<String> url,
				ZonedDateTime startTime, ZonedDateTime endTime, Optional<String> application)
		{
			this.userAnonymized = userAnonymized;
			this.deviceAnonymized = deviceAnonymized;
			this.url = url;
			this.startTime = startTime;
			this.endTime = endTime;
			this.application = application;
		}

		boolean isNetworkActivity()
		{
			return !application.isPresent();
		}

		static ActivityPayload copyTillEndTime(ActivityPayload payload, ZonedDateTime endTime)
		{
			return new ActivityPayload(payload.userAnonymized, payload.deviceAnonymized, payload.url, payload.startTime, endTime,
					payload.application);
		}

		static ActivityPayload copyFromStartTime(ActivityPayload payload, ZonedDateTime startTime)
		{
			return new ActivityPayload(payload.userAnonymized, payload.deviceAnonymized, payload.url, startTime, payload.endTime,
					payload.application);
		}

		static ActivityPayload createInstance(UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized,
				NetworkActivityDto networkActivity)
		{
			ZonedDateTime startTime = networkActivity.getEventTime().orElse(ZonedDateTime.now())
					.withZoneSameInstant(userAnonymized.getTimeZone());
			return new ActivityPayload(userAnonymized, deviceAnonymized, Optional.of(networkActivity.getUrl()), startTime,
					startTime, Optional.empty());
		}

		static ActivityPayload createInstance(UserAnonymizedDto userAnonymized, DeviceAnonymizedDto deviceAnonymized,
				ZonedDateTime startTime, ZonedDateTime endTime, String application)
		{
			ZoneId userTimeZone = userAnonymized.getTimeZone();
			return new ActivityPayload(userAnonymized, deviceAnonymized, Optional.empty(),
					startTime.withZoneSameInstant(userTimeZone), endTime.withZoneSameInstant(userTimeZone),
					Optional.of(application));
		}

		@Override
		public boolean equals(Object other)
		{
			if (other == null)
				return false;
			if (other == this)
				return true;
			if (!(other instanceof ActivityPayload))
				return false;
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
	static class UserAnonymizedEntityHolder
	{
		private final UserAnonymizedService userAnonymizedService;
		private final UUID id;
		private Optional<UserAnonymized> entity = Optional.empty();

		UserAnonymizedEntityHolder(UserAnonymizedService userAnonymizedService, UUID id)
		{
			this.userAnonymizedService = userAnonymizedService;
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
