/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.ActivityRepository;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.analysis.service.AnalysisEngineService.ActivityPayload;
import nu.yona.server.analysis.service.AnalysisEngineService.UserAnonymizedEntityHolder;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.exceptions.AnalysisException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.util.TimeUtil;

@Service
class ActivityUpdateService
{
	private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private UserAnonymizedService userAnonymizedService;
	@Autowired
	private GoalService goalService;
	@Autowired
	private MessageService messageService;
	@Autowired(required = false)
	private DeviceAnonymizedRepository deviceAnonymizedRepository;
	@Autowired(required = false)
	private MessageRepository messageRepository;
	@Autowired(required = false)
	private ActivityRepository activityRepository;
	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;
	@Autowired(required = false)
	private WeekActivityRepository weekActivityRepository;
	@Autowired
	private ActivityCacheService cacheService;

	public void updateLastMonitoredActivityDateIfRelevant(UserAnonymizedEntityHolder userAnonymizedHolder,
			ActivityPayload payload)
	{
		Optional<LocalDate> lastMonitoredActivityDate = payload.userAnonymized.getLastMonitoredActivityDate();
		LocalDate activityEndTime = payload.endTime.toLocalDate();
		if (lastMonitoredActivityDate.map(d -> d.isBefore(activityEndTime)).orElse(true))
		{
			userAnonymizedHolder.getEntity().setLastMonitoredActivityDate(activityEndTime);
		}
	}

	public void addActivity(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload, GoalDto matchingGoal,
			Optional<ActivityDto> lastRegisteredActivity)
	{
		Goal matchingGoalEntity = goalService.getGoalEntityForUserAnonymizedId(payload.userAnonymized.getId(),
				matchingGoal.getGoalId());
		Activity addedActivity = createNewActivity(userAnonymizedHolder.getEntity(), payload, matchingGoalEntity);
		if (shouldUpdateCache(lastRegisteredActivity, addedActivity))
		{
			cacheService.updateLastActivityForUser(payload.userAnonymized.getId(), payload.deviceAnonymized.getId(),
					matchingGoal.getGoalId(), ActivityDto.createInstance(addedActivity));
		}

		// Save first, so the activity is available when saving the message
		userAnonymizedService.updateUserAnonymized(userAnonymizedHolder.getEntity());
		if (matchingGoal.isNoGoGoal()
				&& shouldSendNewGoalConflictMessageForNewConflictingActivity(payload, lastRegisteredActivity))
		{
			sendConflictMessageToAllDestinationsOfUser(userAnonymizedHolder.getEntity(), payload, addedActivity,
					matchingGoalEntity);
		}
	}

	private boolean shouldSendNewGoalConflictMessageForNewConflictingActivity(ActivityPayload payload,
			Optional<ActivityDto> lastRegisteredActivity)
	{
		if (!lastRegisteredActivity.isPresent())
		{
			return true;
		}

		if (isBeyondCombineIntervalWithLastRegisteredActivity(payload, lastRegisteredActivity.get()))
		{
			return true;
		}

		return false;
	}

	private boolean isBeyondCombineIntervalWithLastRegisteredActivity(ActivityPayload payload, ActivityDto lastRegisteredActivity)
	{
		ZonedDateTime intervalEndTime = lastRegisteredActivity.getEndTime()
				.plus(yonaProperties.getAnalysisService().getConflictInterval());
		return payload.startTime.isAfter(intervalEndTime);
	}

	private void sendConflictMessageToAllDestinationsOfUser(UserAnonymized userAnonymized, ActivityPayload payload,
			Activity activity, Goal matchingGoal)
	{
		GoalConflictMessage selfGoalConflictMessage = messageRepository
				.save(GoalConflictMessage.createInstance(payload.userAnonymized.getId(), activity, matchingGoal, payload.url));
		messageService.sendMessage(selfGoalConflictMessage, userAnonymized.getAnonymousDestination());
		// Save the messages, so the other messages can reference it
		userAnonymizedService.updateUserAnonymized(userAnonymized);

		messageService.broadcastMessageToBuddies(payload.userAnonymized,
				() -> createAndSaveBuddyGoalConflictMessage(payload, selfGoalConflictMessage));
	}

	private GoalConflictMessage createAndSaveBuddyGoalConflictMessage(ActivityPayload payload,
			GoalConflictMessage selfGoalConflictMessage)
	{
		GoalConflictMessage message = GoalConflictMessage.createInstanceForBuddy(payload.userAnonymized.getId(),
				selfGoalConflictMessage);
		return messageRepository.save(message);
	}

	public void updateTimeExistingActivity(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload,
			Activity existingActivity)
	{
		LocalDateTime startTimeLocal = payload.startTime.toLocalDateTime();
		if (startTimeLocal.isBefore(existingActivity.getStartTime()))
		{
			existingActivity.setStartTime(startTimeLocal);
		}
		LocalDateTime endTimeLocal = payload.endTime.toLocalDateTime();
		if (endTimeLocal.isAfter(existingActivity.getEndTime()))
		{
			existingActivity.setEndTime(endTimeLocal);
		}

		// Explicitly fetch the entity to indicate that the user entity is dirty
		userAnonymizedHolder.getEntity();
	}

	public void updateTimeLastActivity(UserAnonymizedEntityHolder userAnonymizedHolder, ActivityPayload payload,
			GoalDto matchingGoal, ActivityDto lastRegisteredActivity)
	{
		DayActivity dayActivity = findExistingDayActivity(payload, matchingGoal.getGoalId())
				.orElseThrow(() -> AnalysisException.dayActivityNotFound(payload.userAnonymized.getId(), matchingGoal.getGoalId(),
						payload.startTime, lastRegisteredActivity.getStartTime(), lastRegisteredActivity.getEndTime()));
		// because of the lock further up in this class, we are sure that getLastActivity() gives the same activity
		Activity activity = dayActivity.getLastActivity(payload.deviceAnonymized.getId());
		if (payload.startTime.isBefore(lastRegisteredActivity.getStartTime()))
		{
			activity.setStartTime(payload.startTime.toLocalDateTime());
		}
		if (payload.endTime.isAfter(lastRegisteredActivity.getEndTime()))
		{
			activity.setEndTime(payload.endTime.toLocalDateTime());
		}
		if (shouldUpdateCache(Optional.of(lastRegisteredActivity), activity))
		{
			cacheService.updateLastActivityForUser(payload.userAnonymized.getId(), payload.deviceAnonymized.getId(),
					matchingGoal.getGoalId(), ActivityDto.createInstance(activity));
		}

		// Explicitly fetch the entity to indicate that the user entity is dirty
		userAnonymizedHolder.getEntity();
	}

	private boolean shouldUpdateCache(Optional<ActivityDto> lastRegisteredActivity, Activity newOrUpdatedActivity)
	{
		if (!lastRegisteredActivity.isPresent())
		{
			return true;
		}

		// do not update the cache if the new or updated activity occurs earlier than the last registered activity
		return !newOrUpdatedActivity.getEndTimeAsZonedDateTime().isBefore(lastRegisteredActivity.get().getEndTime());
	}

	private Activity createNewActivity(UserAnonymized userAnonymized, ActivityPayload payload, Goal matchingGoal)
	{
		DayActivity dayActivity = findExistingDayActivity(payload, matchingGoal.getId())
				.orElseGet(() -> createNewDayActivity(userAnonymized, payload, matchingGoal));

		ZonedDateTime endTime = ensureMinimumDurationOneMinute(payload);
		DeviceAnonymized deviceAnonymized = deviceAnonymizedRepository.getOne(payload.deviceAnonymized.getId());
		Activity activity = Activity.createInstance(deviceAnonymized, payload.startTime.getZone(),
				payload.startTime.toLocalDateTime(), endTime.toLocalDateTime(), payload.application);
		activity = activityRepository.save(activity);
		dayActivity.addActivity(activity);
		return activity;
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
		dayActivityRepository.save(dayActivity);

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

	private Optional<DayActivity> findExistingDayActivity(ActivityPayload payload, UUID matchingGoalId)
	{
		return Optional.ofNullable(dayActivityRepository.findOne(payload.userAnonymized.getId(),
				TimeUtil.getStartOfDay(payload.userAnonymized.getTimeZone(), payload.startTime).toLocalDate(), matchingGoalId));
	}
}
