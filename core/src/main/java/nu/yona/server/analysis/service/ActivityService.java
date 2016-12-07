/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

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
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;
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

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private AnalysisEngineProxyService analysisEngineProxyService;

	@Transactional
	public Page<WeekActivityOverviewDTO> getUserWeekActivityOverviews(UUID userID, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityOverviews(userService.getUserAnonymizedID(userID), pageable, mia));
	}

	@Transactional
	public Page<WeekActivityOverviewDTO> getBuddyWeekActivityOverviews(UUID buddyID, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityOverviews(getBuddyUserAnonymizedID(buddyID), pageable, mia));
	}

	private <T> T executeAndCreateInactivityEntries(Function<Set<IntervalInactivityDTO>, T> executor)
	{
		Set<IntervalInactivityDTO> missingInactivities = new HashSet<>();
		// Execute the method that finds missing inactivities
		T retVal = executor.apply(missingInactivities);

		createInactivityEntities(missingInactivities);

		return retVal;
	}

	private void createInactivityEntities(Set<IntervalInactivityDTO> missingInactivities)
	{
		Map<UUID, Set<IntervalInactivityDTO>> activitiesByUserAnonymizedID = missingInactivities.stream()
				.collect(Collectors.groupingBy(mia -> mia.getUserAnonymizedID().get(), Collectors.toSet()));

		activitiesByUserAnonymizedID.forEach((u, mias) -> analysisEngineProxyService.createInactivityEntities(u, mias));
	}

	private Page<WeekActivityOverviewDTO> getWeekActivityOverviews(UUID userAnonymizedID, Pageable pageable,
			Set<IntervalInactivityDTO> missingInactivities)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentWeekDate(userAnonymized), pageable, ChronoUnit.WEEKS);

		Map<LocalDate, Set<WeekActivity>> weekActivityEntitiesByLocalDate = getWeekActivitiesGroupedByDate(userAnonymizedID,
				interval);
		Map<ZonedDateTime, Set<WeekActivity>> weekActivityEntitiesByZonedDate = mapToZonedDateTime(
				weekActivityEntitiesByLocalDate);
		Map<ZonedDateTime, Set<WeekActivityDTO>> weekActivityDTOsByZonedDate = mapWeekActivitiesToDTOs(
				weekActivityEntitiesByZonedDate);
		addMissingInactivity(weekActivityDTOsByZonedDate, interval, ChronoUnit.WEEKS, userAnonymized,
				(goal, startOfWeek) -> createAndSaveWeekInactivity(userAnonymized, goal, startOfWeek, LevelOfDetail.WeekOverview,
						missingInactivities),
				(g, wa) -> createAndSaveInactivityDays(userAnonymized,
						userAnonymized.getGoalsForActivityCategory(g.getActivityCategory()), wa, missingInactivities));
		return new PageImpl<WeekActivityOverviewDTO>(
				weekActivityDTOsByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> WeekActivityOverviewDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, getTotalPageableItems(userAnonymized, ChronoUnit.WEEKS));
	}

	private Map<ZonedDateTime, Set<WeekActivityDTO>> mapWeekActivitiesToDTOs(
			Map<ZonedDateTime, Set<WeekActivity>> weekActivityEntitiesByLocalDate)
	{
		return weekActivityEntitiesByLocalDate.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> mapWeekActivitiesToDTOs(e.getValue())));
	}

	private Set<WeekActivityDTO> mapWeekActivitiesToDTOs(Set<WeekActivity> weekActivityEntities)
	{
		return weekActivityEntities.stream().map(e -> WeekActivityDTO.createInstance(e, LevelOfDetail.WeekOverview))
				.collect(Collectors.toSet());
	}

	private WeekActivityDTO createAndSaveWeekInactivity(UserAnonymizedDTO userAnonymized, Goal goal, ZonedDateTime startOfWeek,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDTO> missingInactivities)
	{
		return WeekActivityDTO.createInstanceInactivity(userAnonymized, goal, startOfWeek, levelOfDetail, missingInactivities);
	}

	private void createAndSaveInactivityDays(UserAnonymizedDTO userAnonymized, Set<GoalDTO> goals, WeekActivityDTO weekActivity,
			Set<IntervalInactivityDTO> missingInactivities)
	{
		weekActivity.createRequiredInactivityDays(userAnonymized, goals, LevelOfDetail.WeekOverview, missingInactivities);
	}

	private long getTotalPageableItems(UserAnonymizedDTO userAnonymized, ChronoUnit timeUnit)
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
		Map<ZonedDateTime, Set<T>> intervalActivityEntitiesByZonedDate = intervalActivityEntitiesByLocalDate.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getValue().iterator().next().getStartTime(), e -> e.getValue()));
		return intervalActivityEntitiesByZonedDate;
	}

	private Map<LocalDate, Set<WeekActivity>> getWeekActivitiesGroupedByDate(UUID userAnonymizedID, Interval interval)
	{
		Set<WeekActivity> weekActivityEntities = weekActivityRepository.findAll(userAnonymizedID, interval.startDate,
				interval.endDate);
		return weekActivityEntities.stream().collect(Collectors.groupingBy(a -> a.getStartDate(), Collectors.toSet()));
	}

	@Transactional
	public Page<DayActivityOverviewDTO<DayActivityDTO>> getUserDayActivityOverviews(UUID userID, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityOverviews(userService.getUserAnonymizedID(userID), pageable, mia));
	}

	@Transactional
	public Page<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> getUserDayActivityOverviewsWithBuddies(UUID userID,
			Pageable pageable)
	{
		UUID userAnonymizedID = userService.getUserAnonymizedID(userID);
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);

		if (!userAnonymized.hasAnyBuddies())
		{
			return new PageImpl<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>(Collections.emptyList(), pageable, 0);
		}

		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Set<BuddyDTO> buddies = buddyService.getBuddiesOfUserThatAcceptedSending(userID);
		Set<UUID> userAnonymizedIDs = buddies.stream().map(b -> getBuddyUserAnonymizedID(b)).collect(Collectors.toSet());
		userAnonymizedIDs.add(userAnonymizedID);
		Map<ZonedDateTime, Set<DayActivityDTO>> dayActivityDTOsByZonedDate = executeAndCreateInactivityEntries(
				mia -> getDayActivitiesForUserAnonymizedIDsInInterval(userAnonymizedIDs, interval, mia));
		List<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> dayActivityOverviews = dayActivityEntitiesToOverviewsUserWithBuddies(
				dayActivityDTOsByZonedDate);
		return new PageImpl<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>(dayActivityOverviews, pageable,
				getTotalPageableItems(userAnonymized, ChronoUnit.DAYS));
	}

	private Map<ZonedDateTime, Set<DayActivityDTO>> getDayActivitiesForUserAnonymizedIDsInInterval(Set<UUID> userAnonymizedIDs,
			Interval interval, Set<IntervalInactivityDTO> mia)
	{
		return userAnonymizedIDs.stream().map(id -> getDayActivities(userAnonymizedService.getUserAnonymized(id), interval, mia))
				.map(Map::entrySet).flatMap(Collection::stream)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
					Set<DayActivityDTO> allActivities = new HashSet<>(a);
					allActivities.addAll(b);
					return allActivities;
				}));
	}

	@Transactional
	public Page<DayActivityOverviewDTO<DayActivityDTO>> getBuddyDayActivityOverviews(UUID buddyID, Pageable pageable)
	{
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityOverviews(getBuddyUserAnonymizedID(buddyID), pageable, mia));
	}

	private UUID getBuddyUserAnonymizedID(UUID buddyID)
	{
		return getBuddyUserAnonymizedID(buddyService.getBuddy(buddyID));
	}

	private UUID getBuddyUserAnonymizedID(BuddyDTO buddy)
	{
		return buddy.getUserAnonymizedID()
				.orElseThrow(() -> new IllegalStateException("Should have user anonymized ID when fetching buddy activity"));
	}

	private Page<DayActivityOverviewDTO<DayActivityDTO>> getDayActivityOverviews(UUID userAnonymizedID, Pageable pageable,
			Set<IntervalInactivityDTO> missingInactivities)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Map<ZonedDateTime, Set<DayActivityDTO>> dayActivitiesByZonedDate = getDayActivities(userAnonymized, interval,
				missingInactivities);
		List<DayActivityOverviewDTO<DayActivityDTO>> dayActivityOverviews = dayActivityDTOsToOverviews(dayActivitiesByZonedDate);

		return new PageImpl<DayActivityOverviewDTO<DayActivityDTO>>(dayActivityOverviews, pageable,
				getTotalPageableItems(userAnonymized, ChronoUnit.DAYS));
	}

	private Map<ZonedDateTime, Set<DayActivityDTO>> getDayActivities(UserAnonymizedDTO userAnonymized, Interval interval,
			Set<IntervalInactivityDTO> missingInactivities)
	{
		Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByLocalDate = getDayActivitiesGroupedByDate(userAnonymized.getID(),
				interval);
		Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate = mapToZonedDateTime(dayActivityEntitiesByLocalDate);
		Map<ZonedDateTime, Set<DayActivityDTO>> dayActivityDTOsByZonedDate = mapDayActivitiesToDTOs(
				dayActivityEntitiesByZonedDate);
		addMissingInactivity(dayActivityDTOsByZonedDate, interval, ChronoUnit.DAYS, userAnonymized,
				(goal, startOfDay) -> createAndSaveDayInactivity(userAnonymized, goal, startOfDay, LevelOfDetail.DayOverview,
						missingInactivities),
				(g, a) -> {
				});
		return dayActivityDTOsByZonedDate;
	}

	private Map<ZonedDateTime, Set<DayActivityDTO>> mapDayActivitiesToDTOs(
			Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate)
	{
		return dayActivityEntitiesByZonedDate.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> mapDayActivitiesToDTOs(e.getValue())));
	}

	private Set<DayActivityDTO> mapDayActivitiesToDTOs(Set<DayActivity> dayActivityEntities)
	{
		return dayActivityEntities.stream().map(e -> DayActivityDTO.createInstance(e, LevelOfDetail.DayOverview))
				.collect(Collectors.toSet());
	}

	private DayActivityDTO createAndSaveDayInactivity(UserAnonymizedDTO userAnonymized, Goal goal, ZonedDateTime startOfDay,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDTO> missingInactivities)
	{
		return DayActivityDTO.createInstanceInactivity(userAnonymized, GoalDTO.createInstance(goal), startOfDay, levelOfDetail,
				missingInactivities);
	}

	private List<DayActivityOverviewDTO<DayActivityDTO>> dayActivityDTOsToOverviews(
			Map<ZonedDateTime, Set<DayActivityDTO>> dayActivityDTOsByZonedDate)
	{
		List<DayActivityOverviewDTO<DayActivityDTO>> dayActivityOverviews = dayActivityDTOsByZonedDate.entrySet().stream()
				.sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
				.map(e -> DayActivityOverviewDTO.createInstanceForUser(e.getKey(), e.getValue())).collect(Collectors.toList());
		return dayActivityOverviews;
	}

	private List<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> dayActivityEntitiesToOverviewsUserWithBuddies(
			Map<ZonedDateTime, Set<DayActivityDTO>> dayActivityDTOsByZonedDate)
	{
		List<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> dayActivityOverviews = dayActivityDTOsByZonedDate.entrySet()
				.stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
				.map(e -> DayActivityOverviewDTO.createInstanceForUserWithBuddies(e.getKey(), e.getValue()))
				.collect(Collectors.toList());
		return dayActivityOverviews;
	}

	private Map<LocalDate, Set<DayActivity>> getDayActivitiesGroupedByDate(UUID userAnonymizedID, Interval interval)
	{
		List<DayActivity> dayActivityEntities = dayActivityRepository
				.findAllActivitiesForUserInIntervalEndIncluded(userAnonymizedID, interval.startDate, interval.endDate);
		return dayActivityEntities.stream().collect(Collectors.groupingBy(a -> a.getStartDate(), Collectors.toSet()));
	}

	private Interval getInterval(LocalDate currentUnitDate, Pageable pageable, ChronoUnit timeUnit)
	{
		LocalDate endDate = currentUnitDate.minus(pageable.getOffset(), timeUnit);
		LocalDate startDate = endDate.minus(pageable.getPageSize() - 1, timeUnit);
		return new Interval(startDate, endDate);
	}

	private LocalDate getCurrentWeekDate(UserAnonymizedDTO userAnonymized)
	{
		LocalDate currentDayDate = getCurrentDayDate(userAnonymized);
		switch (currentDayDate.getDayOfWeek())
		{
			case SUNDAY:
				// take as the first day of week
				return currentDayDate;
			default:
				// MONDAY=1, etc.
				return currentDayDate.minusDays(currentDayDate.getDayOfWeek().getValue());
		}
	}

	private LocalDate getCurrentDayDate(UserAnonymizedDTO userAnonymized)
	{
		return LocalDate.now(userAnonymized.getTimeZone());
	}

	private <T extends IntervalActivityDTO> void addMissingInactivity(Map<ZonedDateTime, Set<T>> activityEntitiesByDate,
			Interval interval, ChronoUnit timeUnit, UserAnonymizedDTO userAnonymized,
			BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier, BiConsumer<Goal, T> existingEntityInactivityCompletor)
	{
		for (LocalDate date = interval.startDate; date.isBefore(interval.endDate)
				|| date.isEqual(interval.endDate); date = date.plus(1, timeUnit))
		{
			ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(userAnonymized.getTimeZone());

			Set<GoalDTO> activeGoals = getActiveGoals(userAnonymized, dateAtStartOfInterval, timeUnit);
			if (activeGoals.isEmpty())
			{
				continue;
			}

			if (!activityEntitiesByDate.containsKey(dateAtStartOfInterval))
			{
				activityEntitiesByDate.put(dateAtStartOfInterval, new HashSet<T>());
			}
			Set<T> activityEntitiesAtDate = activityEntitiesByDate.get(dateAtStartOfInterval);
			activeGoals.stream().map(g -> goalService.getGoalEntityForUserAnonymizedID(userAnonymized.getID(), g.getID()))
					.forEach(g -> addMissingInactivity(g, dateAtStartOfInterval, activityEntitiesAtDate, userAnonymized,
							inactivityEntitySupplier, existingEntityInactivityCompletor));
		}
	}

	private Set<GoalDTO> getActiveGoals(UserAnonymizedDTO userAnonymized, ZonedDateTime dateAtStartOfInterval,
			ChronoUnit timeUnit)
	{
		Set<GoalDTO> activeGoals = userAnonymized.getGoals().stream()
				.filter(g -> g.wasActiveAtInterval(dateAtStartOfInterval, timeUnit)).collect(Collectors.toSet());
		return activeGoals;
	}

	private <T extends IntervalActivityDTO> void addMissingInactivity(Goal activeGoal, ZonedDateTime dateAtStartOfInterval,
			Set<T> activityEntitiesAtDate, UserAnonymizedDTO userAnonymized,
			BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier, BiConsumer<Goal, T> existingEntityInactivityCompletor)
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

	private <T extends IntervalActivityDTO> Optional<T> getActivityForGoal(Set<T> dayActivityDTOsAtDate, Goal goal)
	{
		return dayActivityDTOsAtDate.stream().filter(a -> a.getGoalID().equals(goal.getID())).findAny();
	}

	@Transactional
	public WeekActivityDTO getUserWeekActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityDetail(userID, userService.getUserAnonymizedID(userID), date, goalID, mia));
	}

	@Transactional
	public WeekActivityDTO getBuddyWeekActivityDetail(UUID buddyID, LocalDate date, UUID goalID)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		return executeAndCreateInactivityEntries(
				mia -> getWeekActivityDetail(buddy.getUser().getID(), getBuddyUserAnonymizedID(buddy), date, goalID, mia));
	}

	private WeekActivityDTO getWeekActivityDetail(UUID userID, UUID userAnonymizedID, LocalDate date, UUID goalID,
			Set<IntervalInactivityDTO> missingInactivities)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		WeekActivity weekActivityEntity = weekActivityRepository.findOne(userAnonymizedID, date, goalID);
		if (weekActivityEntity == null)
		{
			return getMissingInactivity(userID, date, goalID, userAnonymized, ChronoUnit.WEEKS,
					(goal, startOfWeek) -> createAndSaveWeekInactivity(userAnonymized, goal, startOfWeek,
							LevelOfDetail.WeekDetail, missingInactivities));
		}
		WeekActivityDTO weekActivityDTO = WeekActivityDTO.createInstance(weekActivityEntity, LevelOfDetail.WeekDetail);
		weekActivityDTO.createRequiredInactivityDays(userAnonymized,
				userAnonymized.getGoalsForActivityCategory(weekActivityEntity.getGoal().getActivityCategory()),
				LevelOfDetail.WeekDetail, missingInactivities);
		return weekActivityDTO;
	}

	@Transactional
	public Page<MessageDTO> getUserWeekActivityDetailMessages(UUID userID, LocalDate date, UUID goalID, Pageable pageable)
	{
		Supplier<IntervalActivity> activitySupplier = () -> weekActivityRepository
				.findOne(userService.getUserAnonymizedID(userID), date, goalID);
		return getActivityDetailMessages(userID, activitySupplier, pageable);
	}

	@Transactional
	public Page<MessageDTO> getBuddyWeekActivityDetailMessages(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			Pageable pageable)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		Supplier<IntervalActivity> activitySupplier = () -> weekActivityRepository.findOne(getBuddyUserAnonymizedID(buddy), date,
				goalID);
		return getActivityDetailMessages(userID, activitySupplier, pageable);
	}

	@Transactional
	public DayActivityDTO getUserDayActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityDetail(userID, userService.getUserAnonymizedID(userID), date, goalID, mia));
	}

	@Transactional
	public DayActivityDTO getBuddyDayActivityDetail(UUID buddyID, LocalDate date, UUID goalID)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		return executeAndCreateInactivityEntries(
				mia -> getDayActivityDetail(buddy.getUser().getID(), getBuddyUserAnonymizedID(buddy), date, goalID, mia));
	}

	private DayActivityDTO getDayActivityDetail(UUID userID, UUID userAnonymizedID, LocalDate date, UUID goalID,
			Set<IntervalInactivityDTO> missingInactivities)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		DayActivity dayActivityEntity = dayActivityRepository.findOne(userAnonymizedID, date, goalID);
		if (dayActivityEntity == null)
		{
			return getMissingInactivity(userID, date, goalID, userAnonymized, ChronoUnit.DAYS,
					(goal, startOfDay) -> createAndSaveDayInactivity(userAnonymized, goal, startOfDay, LevelOfDetail.DayDetail,
							missingInactivities));
		}
		return DayActivityDTO.createInstance(dayActivityEntity, LevelOfDetail.DayDetail);
	}

	@Transactional
	public Page<MessageDTO> getUserDayActivityDetailMessages(UUID userID, LocalDate date, UUID goalID, Pageable pageable)
	{
		Supplier<IntervalActivity> activitySupplier = () -> dayActivityRepository.findOne(userService.getUserAnonymizedID(userID),
				date, goalID);
		return getActivityDetailMessages(userID, activitySupplier, pageable);
	}

	@Transactional
	public Page<MessageDTO> getBuddyDayActivityDetailMessages(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			Pageable pageable)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		Supplier<IntervalActivity> activitySupplier = () -> dayActivityRepository.findOne(getBuddyUserAnonymizedID(buddy), date,
				goalID);
		return getActivityDetailMessages(userID, activitySupplier, pageable);
	}

	private Page<MessageDTO> getActivityDetailMessages(UUID userID, Supplier<IntervalActivity> activitySupplier,
			Pageable pageable)
	{
		IntervalActivity dayActivityEntity = activitySupplier.get();
		if (dayActivityEntity == null)
		{
			return new PageImpl<>(Collections.emptyList());
		}
		return messageService.getActivityRelatedMessages(userID, dayActivityEntity.getID(), pageable);
	}

	@FunctionalInterface
	static interface ActivitySupplier
	{
		IntervalActivity get(BuddyDTO buddy, LocalDate date, UUID goalID);
	}

	@Transactional
	public MessageDTO addMessageToDayActivity(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			PostPutActivityCommentMessageDTO message)
	{
		ActivitySupplier activitySupplier = (b, d, g) -> dayActivityRepository.findOne(getBuddyUserAnonymizedID(b), d, g);
		return addMessageToActivity(userID, buddyID, date, goalID, activitySupplier, message);
	}

	@Transactional
	public MessageDTO addMessageToWeekActivity(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			PostPutActivityCommentMessageDTO message)
	{
		ActivitySupplier activitySupplier = (b, d, g) -> weekActivityRepository.findOne(getBuddyUserAnonymizedID(b), d, g);
		return addMessageToActivity(userID, buddyID, date, goalID, activitySupplier, message);
	}

	@Transactional
	public MessageDTO addMessageToActivity(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			ActivitySupplier activitySupplier, PostPutActivityCommentMessageDTO message)
	{
		UserDTO sendingUser = userService.getPrivateUser(userID);
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		IntervalActivity dayActivityEntity = activitySupplier.get(buddy, date, goalID);
		if (dayActivityEntity == null)
		{
			throw ActivityServiceException.buddyDayActivityNotFound(userID, buddyID, date, goalID);
		}

		return sendMessagePair(sendingUser, getBuddyUserAnonymizedID(buddy), dayActivityEntity.getID(), Optional.empty(),
				Optional.empty(), message.getMessage());
	}

	private MessageDTO sendMessagePair(UserDTO sendingUser, UUID targetUserAnonymizedID, long activityID,
			Optional<ActivityCommentMessage> repliedMessageOfSelf, Optional<ActivityCommentMessage> repliedMessageOfBuddy,
			String message)
	{
		ActivityCommentMessage messageToBuddy = createMessage(sendingUser, activityID,
				repliedMessageOfBuddy.map(ActivityCommentMessage::getThreadHeadMessageID),
				repliedMessageOfBuddy.map(ActivityCommentMessage::getID), false, message);
		ActivityCommentMessage messageToSelf = createMessage(sendingUser, activityID,
				repliedMessageOfSelf.map(ActivityCommentMessage::getThreadHeadMessageID),
				repliedMessageOfSelf.map(ActivityCommentMessage::getID), true, message);
		ActivityCommentMessage savedMessageToSelf = (ActivityCommentMessage) messageService
				.sendMessageToSelfAnonymized(sendingUser, messageToSelf);
		messageToBuddy.setSenderCopyMessage(savedMessageToSelf);
		sendMessage(targetUserAnonymizedID, messageToBuddy);

		return messageService.messageToDTO(sendingUser, savedMessageToSelf);
	}

	private long sendMessage(UUID targetUserAnonymizedID, ActivityCommentMessage messageEntity)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(targetUserAnonymizedID);
		return messageService.sendMessageToUserAnonymized(userAnonymized, messageEntity);
	}

	private ActivityCommentMessage createMessage(UserDTO sendingUser, long activityID, Optional<Long> threadHeadMessageID,
			Optional<Long> repliedMessageID, boolean isSentItem, String message)
	{
		return ActivityCommentMessage.createInstance(sendingUser.getID(), sendingUser.getPrivateData().getUserAnonymizedID(),
				sendingUser.getPrivateData().getNickname(), activityID, isSentItem, message, threadHeadMessageID,
				repliedMessageID);
	}

	private <T extends IntervalActivityDTO> T getMissingInactivity(UUID userID, LocalDate date, UUID goalID,
			UserAnonymizedDTO userAnonymized, ChronoUnit timeUnit, BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier)
	{
		Goal goal = goalService.getGoalEntityForUserAnonymizedID(userAnonymized.getID(), goalID);
		ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(userAnonymized.getTimeZone());
		if (!goal.wasActiveAtInterval(dateAtStartOfInterval, timeUnit))
		{
			throw ActivityServiceException.activityDateGoalMismatch(userID, date, goalID);
		}
		return inactivityEntitySupplier.apply(goal, dateAtStartOfInterval);
	}

	@Transactional
	public MessageDTO replyToMessage(UserDTO sendingUser, ActivityCommentMessage repliedMessage, String message)
	{
		UUID targetUserAnonymizedID = repliedMessage.getRelatedUserAnonymizedID().get();
		return sendMessagePair(sendingUser, targetUserAnonymizedID, repliedMessage.getActivityID(), Optional.of(repliedMessage),
				Optional.of(repliedMessage.getSenderCopyMessage()), message);
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
