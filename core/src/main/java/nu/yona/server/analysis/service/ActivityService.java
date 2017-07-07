/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.analysis.entities.ActivityCommentMessage;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.analysis.service.IntervalActivityDto.LevelOfDetail;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.TimeUtil;

@Service
public class ActivityService
{
	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private GoalService goalService;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Autowired
	private MessageService messageService;

	@Autowired(required = false)
	private WeekActivityRepository weekActivityRepository;

	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;

	@Autowired(required = false)
	private MessageRepository messageRepository;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private AnalysisEngineProxyService analysisEngineProxyService;

	@Transactional
	public Page<WeekActivityOverviewDto> getUserWeekActivityOverviews(UUID userId, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityOverviews(userService.getUserAnonymizedId(userId), pageable, mia));
	}

	@Transactional
	public Page<WeekActivityOverviewDto> getBuddyWeekActivityOverviews(UUID buddyId, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityOverviews(getBuddyUserAnonymizedId(buddyId), pageable, mia));
	}

	private <T> T executeAndCreateInactivityEntries(Function<Set<IntervalInactivityDto>, T> executor)
	{
		Set<IntervalInactivityDto> missingInactivities = new HashSet<>();
		// Execute the method that finds missing inactivities
		T retVal = executor.apply(missingInactivities);

		createInactivityEntities(missingInactivities);

		return retVal;
	}

	private void createInactivityEntities(Set<IntervalInactivityDto> missingInactivities)
	{
		Map<UUID, Set<IntervalInactivityDto>> activitiesByUserAnonymizedId = missingInactivities.stream()
				.collect(Collectors.groupingBy(mia -> mia.getUserAnonymizedId().get(), Collectors.toSet()));

		activitiesByUserAnonymizedId.forEach((u, mias) -> analysisEngineProxyService.createInactivityEntities(u, mias));
	}

	private Page<WeekActivityOverviewDto> getWeekActivityOverviews(UUID userAnonymizedId, Pageable pageable,
			Set<IntervalInactivityDto> missingInactivities)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		Interval interval = getInterval(getCurrentWeekDate(userAnonymized), pageable, ChronoUnit.WEEKS);

		Map<LocalDate, Set<WeekActivity>> weekActivityEntitiesByLocalDate = getWeekActivitiesGroupedByDate(userAnonymizedId,
				interval);
		Map<ZonedDateTime, Set<WeekActivity>> weekActivityEntitiesByZonedDate = mapToZonedDateTime(
				weekActivityEntitiesByLocalDate);
		Map<ZonedDateTime, Set<WeekActivityDto>> weekActivityDtosByZonedDate = mapWeekActivitiesToDtos(
				weekActivityEntitiesByZonedDate);
		addMissingInactivity(userAnonymized.getGoals(), weekActivityDtosByZonedDate, interval, ChronoUnit.WEEKS, userAnonymized,
				(goal, startOfWeek) -> createAndSaveWeekInactivity(userAnonymized, goal, startOfWeek, LevelOfDetail.WEEK_OVERVIEW,
						missingInactivities),
				(g, wa) -> createAndSaveInactivityDays(userAnonymized,
						userAnonymized.getGoalsForActivityCategory(g.getActivityCategory()), wa, missingInactivities));
		return new PageImpl<>(
				weekActivityDtosByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> WeekActivityOverviewDto.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, getTotalPageableItems(userAnonymized, ChronoUnit.WEEKS));
	}

	private Map<ZonedDateTime, Set<WeekActivityDto>> mapWeekActivitiesToDtos(
			Map<ZonedDateTime, Set<WeekActivity>> weekActivityEntitiesByLocalDate)
	{
		return weekActivityEntitiesByLocalDate.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> mapWeekActivitiesToDtos(e.getValue())));
	}

	private Set<WeekActivityDto> mapWeekActivitiesToDtos(Set<WeekActivity> weekActivityEntities)
	{
		return weekActivityEntities.stream().map(e -> WeekActivityDto.createInstance(e, LevelOfDetail.WEEK_OVERVIEW))
				.collect(Collectors.toSet());
	}

	private WeekActivityDto createAndSaveWeekInactivity(UserAnonymizedDto userAnonymized, Goal goal, ZonedDateTime startOfWeek,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDto> missingInactivities)
	{
		return WeekActivityDto.createInstanceInactivity(userAnonymized, goal, startOfWeek, levelOfDetail, missingInactivities);
	}

	private void createAndSaveInactivityDays(UserAnonymizedDto userAnonymized, Set<GoalDto> goals, WeekActivityDto weekActivity,
			Set<IntervalInactivityDto> missingInactivities)
	{
		weekActivity.createRequiredInactivityDays(userAnonymized, goals, LevelOfDetail.WEEK_OVERVIEW, missingInactivities);
	}

	private long getTotalPageableItems(Set<BuddyDto> buddies, ChronoUnit timeUnit)
	{
		return buddies.stream().map(this::getBuddyUserAnonymizedId).map(id -> userAnonymizedService.getUserAnonymized(id))
				.map(ua -> getTotalPageableItems(ua, timeUnit)).max(Long::compareTo).orElse(0L);
	}

	private long getTotalPageableItems(UserAnonymizedDto userAnonymized, ChronoUnit timeUnit)
	{
		long activityMemoryDays = yonaProperties.getAnalysisService().getActivityMemory().toDays();
		Optional<LocalDateTime> oldestGoalCreationTime = userAnonymized.getOldestGoalCreationTime();
		long activityRecordedDays = oldestGoalCreationTime.isPresent()
				? (Duration.between(oldestGoalCreationTime.get(), TimeUtil.utcNow()).toDays() + 1) : 0;
		long totalDays = Math.min(activityRecordedDays, activityMemoryDays);
		switch (timeUnit)
		{
			case WEEKS:
				return (long) Math.ceil((double) totalDays / 7);
			case DAYS:
				return totalDays;
			default:
				throw new IllegalArgumentException("timeUnit should be weeks or days");
		}
	}

	private <T extends IntervalActivity> Map<ZonedDateTime, Set<T>> mapToZonedDateTime(
			Map<LocalDate, Set<T>> intervalActivityEntitiesByLocalDate)
	{
		return intervalActivityEntitiesByLocalDate.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getValue().iterator().next().getStartTime(), Map.Entry::getValue));
	}

	private Map<LocalDate, Set<WeekActivity>> getWeekActivitiesGroupedByDate(UUID userAnonymizedId, Interval interval)
	{
		Set<WeekActivity> weekActivityEntities = weekActivityRepository.findAll(userAnonymizedId, interval.startDate,
				interval.endDate);
		return weekActivityEntities.stream().collect(Collectors.groupingBy(IntervalActivity::getStartDate, Collectors.toSet()));
	}

	@Transactional
	public Page<DayActivityOverviewDto<DayActivityDto>> getUserDayActivityOverviews(UUID userId, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityOverviews(userService.getUserAnonymizedId(userId), pageable, mia));
	}

	@Transactional
	public Page<DayActivityOverviewDto<DayActivityWithBuddiesDto>> getUserDayActivityOverviewsWithBuddies(UUID userId,
			Pageable pageable)
	{
		UUID userAnonymizedId = userService.getUserAnonymizedId(userId);
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);

		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);
		Set<BuddyDto> buddies = buddyService.getBuddiesOfUserThatAcceptedSending(userId);
		Set<UUID> userAnonymizedIds = buddies.stream().map(this::getBuddyUserAnonymizedId).collect(Collectors.toSet());
		userAnonymizedIds.add(userAnonymizedId);
		// Goals of the user should only be included in the withBuddies list when at least one buddy has a goal in that category
		Set<UUID> activityCategoryIdsUsedByBuddies = buddies.stream().map(BuddyDto::getGoals).flatMap(Set::stream)
				.map(GoalDto::getActivityCategoryId).collect(Collectors.toSet());

		Map<ZonedDateTime, Set<DayActivityDto>> dayActivityDtosByZonedDate = executeAndCreateInactivityEntries(
				mia -> getDayActivitiesForUserAnonymizedIdsInInterval(userAnonymizedIds, activityCategoryIdsUsedByBuddies,
						interval, mia));
		List<DayActivityOverviewDto<DayActivityWithBuddiesDto>> dayActivityOverviews = dayActivityEntitiesToOverviewsUserWithBuddies(
				dayActivityDtosByZonedDate);
		return new PageImpl<>(dayActivityOverviews, pageable, getTotalPageableItems(buddies, ChronoUnit.DAYS));
	}

	private Map<ZonedDateTime, Set<DayActivityDto>> getDayActivitiesForUserAnonymizedIdsInInterval(Set<UUID> userAnonymizedIds,
			Set<UUID> relevantActivityCategoryIds, Interval interval, Set<IntervalInactivityDto> mia)
	{
		return userAnonymizedIds.stream()
				.map(id -> getDayActivitiesForCategories(userAnonymizedService.getUserAnonymized(id), relevantActivityCategoryIds,
						interval, mia))
				.map(Map::entrySet).flatMap(Collection::stream)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
					Set<DayActivityDto> allActivities = new HashSet<>(a);
					allActivities.addAll(b);
					return allActivities;
				}));
	}

	private Map<ZonedDateTime, Set<DayActivityDto>> getDayActivitiesForCategories(UserAnonymizedDto userAnonymizedDto,
			Set<UUID> relevantActivityCategoryIds, Interval interval, Set<IntervalInactivityDto> mia)
	{
		Set<GoalDto> relevantGoals = userAnonymizedDto.getGoals().stream()
				.filter(g -> relevantActivityCategoryIds.contains(g.getActivityCategoryId())).collect(Collectors.toSet());
		return getDayActivities(userAnonymizedDto, relevantGoals, interval, mia);
	}

	@Transactional
	public Page<DayActivityOverviewDto<DayActivityDto>> getBuddyDayActivityOverviews(UUID buddyId, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityOverviews(getBuddyUserAnonymizedId(buddyId), pageable, mia));
	}

	private UUID getBuddyUserAnonymizedId(UUID buddyId)
	{
		return getBuddyUserAnonymizedId(buddyService.getBuddy(buddyId));
	}

	private UUID getBuddyUserAnonymizedId(BuddyDto buddy)
	{
		return buddy.getUserAnonymizedId()
				.orElseThrow(() -> new IllegalStateException("Should have user anonymized ID when fetching buddy activity"));
	}

	private Page<DayActivityOverviewDto<DayActivityDto>> getDayActivityOverviews(UUID userAnonymizedId, Pageable pageable,
			Set<IntervalInactivityDto> missingInactivities)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Map<ZonedDateTime, Set<DayActivityDto>> dayActivitiesByZonedDate = getDayActivities(userAnonymized, interval,
				missingInactivities);
		List<DayActivityOverviewDto<DayActivityDto>> dayActivityOverviews = dayActivityDtosToOverviews(dayActivitiesByZonedDate);

		return new PageImpl<>(dayActivityOverviews, pageable, getTotalPageableItems(userAnonymized, ChronoUnit.DAYS));
	}

	private Map<ZonedDateTime, Set<DayActivityDto>> getDayActivities(UserAnonymizedDto userAnonymized, Interval interval,
			Set<IntervalInactivityDto> missingInactivities)
	{
		return getDayActivities(userAnonymized, userAnonymized.getGoals(), interval, missingInactivities);
	}

	private Map<ZonedDateTime, Set<DayActivityDto>> getDayActivities(UserAnonymizedDto userAnonymized, Set<GoalDto> relevantGoals,
			Interval interval, Set<IntervalInactivityDto> missingInactivities)
	{
		Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByLocalDate = getDayActivitiesGroupedByDate(userAnonymized.getId(),
				relevantGoals, interval);
		Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate = mapToZonedDateTime(dayActivityEntitiesByLocalDate);
		Map<ZonedDateTime, Set<DayActivityDto>> dayActivityDtosByZonedDate = mapDayActivitiesToDtos(
				dayActivityEntitiesByZonedDate);
		addMissingInactivity(relevantGoals, dayActivityDtosByZonedDate, interval, ChronoUnit.DAYS, userAnonymized,
				(goal, startOfDay) -> createDayInactivity(userAnonymized, goal, startOfDay, LevelOfDetail.DAY_OVERVIEW,
						missingInactivities),
				(g, a) -> { // Nothing needed here
				});
		return dayActivityDtosByZonedDate;
	}

	private Map<ZonedDateTime, Set<DayActivityDto>> mapDayActivitiesToDtos(
			Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate)
	{
		return dayActivityEntitiesByZonedDate.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> mapDayActivitiesToDtos(e.getValue())));
	}

	private Set<DayActivityDto> mapDayActivitiesToDtos(Set<DayActivity> dayActivityEntities)
	{
		return dayActivityEntities.stream().map(e -> DayActivityDto.createInstance(e, LevelOfDetail.DAY_OVERVIEW))
				.collect(Collectors.toSet());
	}

	private DayActivityDto createDayInactivity(UserAnonymizedDto userAnonymized, Goal goal, ZonedDateTime startOfDay,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDto> missingInactivities)
	{
		return DayActivityDto.createInstanceInactivity(userAnonymized, GoalDto.createInstance(goal), startOfDay, levelOfDetail,
				missingInactivities);
	}

	private List<DayActivityOverviewDto<DayActivityDto>> dayActivityDtosToOverviews(
			Map<ZonedDateTime, Set<DayActivityDto>> dayActivityDtosByZonedDate)
	{
		return dayActivityDtosByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
				.map(e -> DayActivityOverviewDto.createInstanceForUser(e.getKey(), e.getValue())).collect(Collectors.toList());
	}

	private List<DayActivityOverviewDto<DayActivityWithBuddiesDto>> dayActivityEntitiesToOverviewsUserWithBuddies(
			Map<ZonedDateTime, Set<DayActivityDto>> dayActivityDtosByZonedDate)
	{
		return dayActivityDtosByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
				.map(e -> DayActivityOverviewDto.createInstanceForUserWithBuddies(e.getKey(), e.getValue()))
				.collect(Collectors.toList());
	}

	private Map<LocalDate, Set<DayActivity>> getDayActivitiesGroupedByDate(UUID userAnonymizedId, Set<GoalDto> relevantGoals,
			Interval interval)
	{
		List<DayActivity> dayActivityEntities = findAllActivitiesForUserInIntervalEndIncluded(userAnonymizedId, relevantGoals,
				interval);
		return dayActivityEntities.stream().collect(Collectors.groupingBy(IntervalActivity::getStartDate, Collectors.toSet()));
	}

	private List<DayActivity> findAllActivitiesForUserInIntervalEndIncluded(UUID userAnonymizedId, Set<GoalDto> relevantGoals,
			Interval interval)
	{
		if (relevantGoals.isEmpty())
		{
			// SQL in-query fails when the list is empty, so don't go to the repository with an empty list
			return Collections.emptyList();
		}
		return dayActivityRepository.findAllActivitiesForUserInIntervalEndIncluded(userAnonymizedId,
				relevantGoals.stream().map(GoalDto::getGoalId).collect(Collectors.toSet()), interval.startDate, interval.endDate);
	}

	private Interval getInterval(LocalDate currentUnitDate, Pageable pageable, ChronoUnit timeUnit)
	{
		LocalDate endDate = currentUnitDate.minus(pageable.getOffset(), timeUnit);
		LocalDate startDate = endDate.minus(pageable.getPageSize() - 1L, timeUnit);
		return new Interval(startDate, endDate);
	}

	private LocalDate getCurrentWeekDate(UserAnonymizedDto userAnonymized)
	{
		LocalDate currentDayDate = getCurrentDayDate(userAnonymized);
		if (currentDayDate.getDayOfWeek() == DayOfWeek.SUNDAY)
		{
			// take as the first day of week
			return currentDayDate;
		}
		// MONDAY=1, etc.
		return currentDayDate.minusDays(currentDayDate.getDayOfWeek().getValue());
	}

	private LocalDate getCurrentDayDate(UserAnonymizedDto userAnonymized)
	{
		return LocalDate.now(userAnonymized.getTimeZone());
	}

	private <T extends IntervalActivityDto> void addMissingInactivity(Set<GoalDto> relevantGoals,
			Map<ZonedDateTime, Set<T>> activityEntitiesByDate, Interval interval, ChronoUnit timeUnit,
			UserAnonymizedDto userAnonymized, BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier,
			BiConsumer<Goal, T> existingEntityInactivityCompletor)
	{
		for (LocalDate date = interval.startDate; date.isBefore(interval.endDate)
				|| date.isEqual(interval.endDate); date = date.plus(1, timeUnit))
		{
			ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(userAnonymized.getTimeZone());

			Set<GoalDto> activeGoals = getActiveGoals(relevantGoals, dateAtStartOfInterval, timeUnit);
			if (activeGoals.isEmpty())
			{
				continue;
			}

			if (!activityEntitiesByDate.containsKey(dateAtStartOfInterval))
			{
				activityEntitiesByDate.put(dateAtStartOfInterval, new HashSet<T>());
			}
			Set<T> activityEntitiesAtDate = activityEntitiesByDate.get(dateAtStartOfInterval);
			activeGoals.stream().map(g -> goalService.getGoalEntityForUserAnonymizedId(userAnonymized.getId(), g.getGoalId()))
					.forEach(g -> addMissingInactivity(g, dateAtStartOfInterval, activityEntitiesAtDate, inactivityEntitySupplier,
							existingEntityInactivityCompletor));
		}
	}

	private Set<GoalDto> getActiveGoals(Set<GoalDto> relevantGoals, ZonedDateTime dateAtStartOfInterval, ChronoUnit timeUnit)
	{
		return relevantGoals.stream().filter(g -> g.wasActiveAtInterval(dateAtStartOfInterval, timeUnit))
				.collect(Collectors.toSet());
	}

	private <T extends IntervalActivityDto> void addMissingInactivity(Goal activeGoal, ZonedDateTime dateAtStartOfInterval,
			Set<T> activityEntitiesAtDate, BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier,
			BiConsumer<Goal, T> existingEntityInactivityCompletor)
	{
		Optional<T> activityForGoal = getActivityForGoal(activityEntitiesAtDate, activeGoal);
		if (activityForGoal.isPresent())
		{
			// even if activity was already recorded, it might be that this is not for the complete period
			// so make the interval activity complete with a consumer
			existingEntityInactivityCompletor.accept(activeGoal, activityForGoal.get());
		}
		else
		{
			activityEntitiesAtDate.add(inactivityEntitySupplier.apply(activeGoal, dateAtStartOfInterval));
		}
	}

	private <T extends IntervalActivityDto> Optional<T> getActivityForGoal(Set<T> dayActivityDtosAtDate, Goal goal)
	{
		return dayActivityDtosAtDate.stream().filter(a -> a.getGoalId().equals(goal.getId())).findAny();
	}

	@Transactional
	public WeekActivityDto getUserWeekActivityDetail(UUID userId, LocalDate date, UUID goalId)
	{
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityDetail(userId, userService.getUserAnonymizedId(userId), date, goalId, mia));
	}

	@Transactional
	public WeekActivityDto getBuddyWeekActivityDetail(UUID buddyId, LocalDate date, UUID goalId)
	{
		BuddyDto buddy = buddyService.getBuddy(buddyId);
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityDetail(buddy.getUser().getId(), getBuddyUserAnonymizedId(buddy), date, goalId, mia));
	}

	private WeekActivityDto getWeekActivityDetail(UUID userId, UUID userAnonymizedId, LocalDate date, UUID goalId,
			Set<IntervalInactivityDto> missingInactivities)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		WeekActivity weekActivityEntity = weekActivityRepository.findOne(userAnonymizedId, date, goalId);
		if (weekActivityEntity == null)
		{
			return getMissingInactivity(userId, date, goalId, userAnonymized, ChronoUnit.WEEKS,
					(goal, startOfWeek) -> createAndSaveWeekInactivity(userAnonymized, goal, startOfWeek,
							LevelOfDetail.WEEK_DETAIL, missingInactivities));
		}
		WeekActivityDto weekActivityDto = WeekActivityDto.createInstance(weekActivityEntity, LevelOfDetail.WEEK_DETAIL);
		weekActivityDto.createRequiredInactivityDays(userAnonymized,
				userAnonymized.getGoalsForActivityCategory(weekActivityEntity.getGoal().getActivityCategory()),
				LevelOfDetail.WEEK_DETAIL, missingInactivities);
		return weekActivityDto;
	}

	@Transactional
	public Page<MessageDto> getUserWeekActivityDetailMessages(UUID userId, LocalDate date, UUID goalId, Pageable pageable)
	{
		Supplier<IntervalActivity> activitySupplier = () -> weekActivityRepository
				.findOne(userService.getUserAnonymizedId(userId), date, goalId);
		return getActivityDetailMessages(userId, activitySupplier, pageable);
	}

	@Transactional
	public Page<MessageDto> getBuddyWeekActivityDetailMessages(UUID userId, UUID buddyId, LocalDate date, UUID goalId,
			Pageable pageable)
	{
		BuddyDto buddy = buddyService.getBuddy(buddyId);
		Supplier<IntervalActivity> activitySupplier = () -> weekActivityRepository.findOne(getBuddyUserAnonymizedId(buddy), date,
				goalId);
		return getActivityDetailMessages(userId, activitySupplier, pageable);
	}

	@Transactional
	public DayActivityDto getUserDayActivityDetail(UUID userId, LocalDate date, UUID goalId)
	{
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityDetail(userId, userService.getUserAnonymizedId(userId), date, goalId, mia));
	}

	@Transactional
	public DayActivityDto getBuddyDayActivityDetail(UUID buddyId, LocalDate date, UUID goalId)
	{
		BuddyDto buddy = buddyService.getBuddy(buddyId);
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityDetail(buddy.getUser().getId(), getBuddyUserAnonymizedId(buddy), date, goalId, mia));
	}

	private DayActivityDto getDayActivityDetail(UUID userId, UUID userAnonymizedId, LocalDate date, UUID goalId,
			Set<IntervalInactivityDto> missingInactivities)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
		DayActivity dayActivityEntity = dayActivityRepository.findOne(userAnonymizedId, date, goalId);
		if (dayActivityEntity == null)
		{
			return getMissingInactivity(userId, date, goalId, userAnonymized, ChronoUnit.DAYS,
					(goal, startOfDay) -> createDayInactivity(userAnonymized, goal, startOfDay, LevelOfDetail.DAY_DETAIL,
							missingInactivities));
		}
		return DayActivityDto.createInstance(dayActivityEntity, LevelOfDetail.DAY_DETAIL);
	}

	@Transactional
	public Page<MessageDto> getUserDayActivityDetailMessages(UUID userId, LocalDate date, UUID goalId, Pageable pageable)
	{
		Supplier<IntervalActivity> activitySupplier = () -> dayActivityRepository.findOne(userService.getUserAnonymizedId(userId),
				date, goalId);
		return getActivityDetailMessages(userId, activitySupplier, pageable);
	}

	@Transactional
	public Page<MessageDto> getBuddyDayActivityDetailMessages(UUID userId, UUID buddyId, LocalDate date, UUID goalId,
			Pageable pageable)
	{
		BuddyDto buddy = buddyService.getBuddy(buddyId);
		Supplier<IntervalActivity> activitySupplier = () -> dayActivityRepository.findOne(getBuddyUserAnonymizedId(buddy), date,
				goalId);
		return getActivityDetailMessages(userId, activitySupplier, pageable);
	}

	private Page<MessageDto> getActivityDetailMessages(UUID userId, Supplier<IntervalActivity> activitySupplier,
			Pageable pageable)
	{
		IntervalActivity dayActivityEntity = activitySupplier.get();
		if (dayActivityEntity == null)
		{
			return new PageImpl<>(Collections.emptyList());
		}
		return messageService.getActivityRelatedMessages(userId, dayActivityEntity, pageable);
	}

	@FunctionalInterface
	static interface ActivitySupplier
	{
		IntervalActivity get(BuddyDto buddy, LocalDate date, UUID goalId);
	}

	@Transactional
	public MessageDto addMessageToDayActivity(UUID userId, UUID buddyId, LocalDate date, UUID goalId,
			PostPutActivityCommentMessageDto message)
	{
		ActivitySupplier activitySupplier = (b, d, g) -> dayActivityRepository.findOne(getBuddyUserAnonymizedId(b), d, g);
		return addMessageToActivity(userId, buddyId, date, goalId, activitySupplier, message);
	}

	@Transactional
	public MessageDto addMessageToWeekActivity(UUID userId, UUID buddyId, LocalDate date, UUID goalId,
			PostPutActivityCommentMessageDto message)
	{
		ActivitySupplier activitySupplier = (b, d, g) -> weekActivityRepository.findOne(getBuddyUserAnonymizedId(b), d, g);
		return addMessageToActivity(userId, buddyId, date, goalId, activitySupplier, message);
	}

	@Transactional
	public MessageDto addMessageToActivity(UUID userId, UUID buddyId, LocalDate date, UUID goalId,
			ActivitySupplier activitySupplier, PostPutActivityCommentMessageDto message)
	{
		UserDto sendingUser = userService.getPrivateUser(userId);
		BuddyDto buddy = buddyService.getBuddy(buddyId);
		IntervalActivity dayActivityEntity = activitySupplier.get(buddy, date, goalId);
		if (dayActivityEntity == null)
		{
			throw ActivityServiceException.buddyDayActivityNotFound(userId, buddyId, date, goalId);
		}

		return sendMessagePair(sendingUser, getBuddyUserAnonymizedId(buddy), dayActivityEntity, Optional.empty(),
				Optional.empty(), message.getMessage());
	}

	private MessageDto sendMessagePair(UserDto sendingUser, UUID targetUserAnonymizedId, IntervalActivity intervalActivityEntity,
			Optional<Message> repliedMessageOfSelf, Optional<Message> repliedMessageOfBuddy, String message)
	{
		UUID sendingUserAnonymizedId = sendingUser.getPrivateData().getUserAnonymizedId();
		ActivityCommentMessage messageToBuddy = createMessage(sendingUser, sendingUserAnonymizedId, intervalActivityEntity,
				repliedMessageOfBuddy, false, message);
		ActivityCommentMessage messageToSelf = createMessage(sendingUser, targetUserAnonymizedId, intervalActivityEntity,
				repliedMessageOfSelf, true, message);
		sendMessage(targetUserAnonymizedId, messageToBuddy);
		messageToSelf.setBuddyMessage(messageToBuddy);
		sendMessage(sendingUserAnonymizedId, messageToSelf);

		return messageService.messageToDto(sendingUser, messageToSelf);
	}

	private void sendMessage(UUID targetUserAnonymizedId, ActivityCommentMessage messageEntity)
	{
		UserAnonymized userAnonymizedEntity = userAnonymizedService.getUserAnonymizedEntity(targetUserAnonymizedId);
		messageService.sendMessage(messageEntity, userAnonymizedEntity.getAnonymousDestination());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity);
	}

	private ActivityCommentMessage createMessage(UserDto sendingUser, UUID relatedUserAnonymizedId,
			IntervalActivity intervalActivityEntity, Optional<Message> repliedMessage, boolean isSentItem, String messageText)
	{
		ActivityCommentMessage message;
		if (repliedMessage.isPresent())
		{
			message = ActivityCommentMessage.createInstance(sendingUser.getId(), relatedUserAnonymizedId,
					sendingUser.getPrivateData().getNickname(), intervalActivityEntity, isSentItem, messageText,
					repliedMessage.get());
		}
		else
		{
			message = ActivityCommentMessage.createThreadHeadInstance(sendingUser.getId(), relatedUserAnonymizedId,
					sendingUser.getPrivateData().getNickname(), intervalActivityEntity, isSentItem, messageText);
		}
		messageRepository.save(message);
		return message;
	}

	private <T extends IntervalActivityDto> T getMissingInactivity(UUID userId, LocalDate date, UUID goalId,
			UserAnonymizedDto userAnonymized, ChronoUnit timeUnit, BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier)
	{
		Goal goal = goalService.getGoalEntityForUserAnonymizedId(userAnonymized.getId(), goalId);
		ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(userAnonymized.getTimeZone());
		if (!goal.wasActiveAtInterval(dateAtStartOfInterval, timeUnit))
		{
			throw ActivityServiceException.activityDateGoalMismatch(userId, date, goalId);
		}
		return inactivityEntitySupplier.apply(goal, dateAtStartOfInterval);
	}

	@Transactional
	public MessageDto replyToMessage(UserDto sendingUser, ActivityCommentMessage repliedMessage, String message)
	{
		UUID targetUserAnonymizedId = repliedMessage.getRelatedUserAnonymizedId().get();
		return sendMessagePair(sendingUser, targetUserAnonymizedId, repliedMessage.getIntervalActivity(),
				Optional.of(repliedMessage), Optional.of(repliedMessage.getSenderCopyMessage()), message);
	}

	private class Interval
	{
		public final LocalDate startDate;
		public final LocalDate endDate;

		public Interval(LocalDate startDate, LocalDate endDate)
		{
			this.startDate = startDate;
			this.endDate = endDate;
		}
	}
}
