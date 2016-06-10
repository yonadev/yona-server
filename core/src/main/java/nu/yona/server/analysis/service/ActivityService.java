package nu.yona.server.analysis.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalServiceException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class ActivityService
{
	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private WeekActivityRepository weekActivityRepository;

	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;

	@Autowired
	private YonaProperties yonaProperties;

	@Transactional
	public Page<WeekActivityOverviewDTO> getUserWeekActivityOverviews(UUID userID, Pageable pageable)
	{
		return getWeekActivityOverviews(userService.getUserAnonymizedID(userID), pageable);
	}

	@Transactional
	public Page<WeekActivityOverviewDTO> getBuddyWeekActivityOverviews(UUID buddyID, Pageable pageable)
	{
		return getWeekActivityOverviews(buddyService.getBuddy(buddyID).getUserAnonymizedID(), pageable);
	}

	private Page<WeekActivityOverviewDTO> getWeekActivityOverviews(UUID userAnonymizedID, Pageable pageable)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentWeekDate(userAnonymized), pageable, ChronoUnit.WEEKS);

		Map<LocalDate, Set<WeekActivity>> weekActivityEntitiesByLocalDate = getWeekActivitiesGroupedByDate(userAnonymizedID,
				interval);
		addMissingInactivity(weekActivityEntitiesByLocalDate, interval, ChronoUnit.WEEKS, userAnonymized,
				(goal, startOfWeek) -> WeekActivity.createInstanceInactivity(null, goal, startOfWeek),
				a -> a.addInactivityDaysIfNeeded());
		Map<ZonedDateTime, Set<WeekActivity>> weekActivityEntitiesByZonedDate = mapToZonedDateTime(
				weekActivityEntitiesByLocalDate);
		return new PageImpl<WeekActivityOverviewDTO>(
				weekActivityEntitiesByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> WeekActivityOverviewDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, getTotalPageableItems(userAnonymized, ChronoUnit.WEEKS));
	}

	private long getTotalPageableItems(UserAnonymizedDTO userAnonymized, ChronoUnit timeUnit)
	{
		long activityMemoryDays = yonaProperties.getAnalysisService().getActivityMemory().toDays();
		Optional<ZonedDateTime> oldestGoalCreationTime = userAnonymized.getOldestGoalCreationTime();
		long activityRecordedDays = oldestGoalCreationTime.isPresent() ? (Duration
				.between(oldestGoalCreationTime.get(), ZonedDateTime.now(ZoneId.of(userAnonymized.getTimeZoneId()))).toDays() + 1)
				: 0;
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
		return weekActivityEntities.stream().collect(Collectors.groupingBy(a -> a.getDate(), Collectors.toSet()));
	}

	@Transactional
	public Page<DayActivityOverviewDTO> getUserDayActivityOverviews(UUID userID, Pageable pageable)
	{
		return getDayActivityOverviews(userService.getUserAnonymizedID(userID), pageable);
	}

	@Transactional
	public Page<DayActivityOverviewDTO> getBuddyDayActivityOverviews(UUID buddyID, Pageable pageable)
	{
		return getDayActivityOverviews(buddyService.getBuddy(buddyID).getUserAnonymizedID(), pageable);
	}

	private Page<DayActivityOverviewDTO> getDayActivityOverviews(UUID userAnonymizedID, Pageable pageable)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByLocalDate = getDayActivitiesGroupedByDate(userAnonymizedID,
				interval);
		addMissingInactivity(dayActivityEntitiesByLocalDate, interval, ChronoUnit.DAYS, userAnonymized,
				(goal, startOfDay) -> DayActivity.createInstanceInactivity(null, goal, startOfDay), a -> {
				});
		Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate = mapToZonedDateTime(dayActivityEntitiesByLocalDate);
		return new PageImpl<DayActivityOverviewDTO>(
				dayActivityEntitiesByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> DayActivityOverviewDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, getTotalPageableItems(userAnonymized, ChronoUnit.DAYS));
	}

	private Map<LocalDate, Set<DayActivity>> getDayActivitiesGroupedByDate(UUID userAnonymizedID, Interval interval)
	{
		Set<DayActivity> dayActivityEntities = dayActivityRepository.findAll(userAnonymizedID, interval.startDate,
				interval.endDate);
		return dayActivityEntities.stream().collect(Collectors.groupingBy(a -> a.getDate(), Collectors.toSet()));
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
		return LocalDate.now(ZoneId.of(userAnonymized.getTimeZoneId()));
	}

	private <T extends IntervalActivity> void addMissingInactivity(Map<LocalDate, Set<T>> activityEntitiesByDate,
			Interval interval, ChronoUnit timeUnit, UserAnonymizedDTO userAnonymized,
			BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier, Consumer<T> existingEntityInactivityCompletor)
	{
		for (LocalDate date = interval.startDate; date.isBefore(interval.endDate)
				|| date.isEqual(interval.endDate); date = date.plus(1, timeUnit))
		{
			ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(ZoneId.of(userAnonymized.getTimeZoneId()));

			Set<Goal> activeGoals = getActiveGoals(userAnonymized, dateAtStartOfInterval, timeUnit);
			if (activeGoals.isEmpty())
			{
				continue;
			}

			if (!activityEntitiesByDate.containsKey(date))
			{
				activityEntitiesByDate.put(date, new HashSet<T>());
			}
			Set<T> activityEntitiesAtDate = activityEntitiesByDate.get(date);
			activeGoals.forEach(g -> addMissingInactivity(g, dateAtStartOfInterval, activityEntitiesAtDate, userAnonymized,
					inactivityEntitySupplier, existingEntityInactivityCompletor));
		}
	}

	private Set<Goal> getActiveGoals(UserAnonymizedDTO userAnonymized, ZonedDateTime dateAtStartOfInterval, ChronoUnit timeUnit)
	{
		Set<Goal> activeGoals = userAnonymized.getGoals().stream()
				.filter(g -> g.wasActiveAtInterval(dateAtStartOfInterval, timeUnit)).collect(Collectors.toSet());
		return activeGoals;
	}

	private <T extends IntervalActivity> void addMissingInactivity(Goal activeGoal, ZonedDateTime dateAtStartOfInterval,
			Set<T> activityEntitiesAtDate, UserAnonymizedDTO userAnonymized,
			BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier, Consumer<T> existingEntityInactivityCompletor)
	{
		Optional<T> activityForGoal = getActivityForGoal(activityEntitiesAtDate, activeGoal);
		if (activityForGoal.isPresent())
		{
			// even if activity was already recorded, it might be that this is not for the complete period
			// so make the interval activity complete with a consumer
			existingEntityInactivityCompletor.accept(activityForGoal.get());
		}
		else
		{
			activityEntitiesAtDate.add(inactivityEntitySupplier.apply(activeGoal, dateAtStartOfInterval));
		}
	}

	private <T extends IntervalActivity> Optional<T> getActivityForGoal(Set<T> dayActivityEntitiesAtDate, Goal goal)
	{
		return dayActivityEntitiesAtDate.stream().filter(a -> a.getGoal().equals(goal)).findAny();
	}

	@Transactional
	public WeekActivityDTO getUserWeekActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		return getWeekActivityDetail(userID, userService.getUserAnonymizedID(userID), date, goalID);
	}

	@Transactional
	public WeekActivityDTO getBuddyWeekActivityDetail(UUID buddyID, LocalDate date, UUID goalID)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		return getWeekActivityDetail(buddy.getUser().getID(), buddy.getUserAnonymizedID(), date, goalID);
	}

	private WeekActivityDTO getWeekActivityDetail(UUID userID, UUID userAnonymizedID, LocalDate date, UUID goalID)
	{
		WeekActivity weekActivityEntity = weekActivityRepository.findOne(userAnonymizedID, date, goalID);
		if (weekActivityEntity == null)
		{
			weekActivityEntity = getMissingInactivity(userID, date, goalID, userAnonymizedID, ChronoUnit.WEEKS,
					(goal, startOfWeek) -> WeekActivity.createInstanceInactivity(null, goal, startOfWeek));
		}
		return WeekActivityDTO.createInstance(weekActivityEntity, LevelOfDetail.WeekDetail);
	}

	@Transactional
	public DayActivityDTO getUserDayActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		return getDayActivityDetail(userID, userService.getUserAnonymizedID(userID), date, goalID);
	}

	@Transactional
	public DayActivityDTO getBuddyDayActivityDetail(UUID buddyID, LocalDate date, UUID goalID)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		return getDayActivityDetail(buddy.getUser().getID(), buddy.getUserAnonymizedID(), date, goalID);
	}

	private DayActivityDTO getDayActivityDetail(UUID userID, UUID userAnonymizedID, LocalDate date, UUID goalID)
	{
		DayActivity dayActivityEntity = dayActivityRepository.findOne(userAnonymizedID, date, goalID);
		if (dayActivityEntity == null)
		{
			dayActivityEntity = getMissingInactivity(userID, date, goalID, userAnonymizedID, ChronoUnit.DAYS,
					(goal, startOfDay) -> DayActivity.createInstanceInactivity(null, goal, startOfDay));
		}
		return DayActivityDTO.createInstance(dayActivityEntity, LevelOfDetail.DayDetail);
	}

	private <T extends IntervalActivity> T getMissingInactivity(UUID userID, LocalDate date, UUID goalID, UUID userAnonymizedID,
			ChronoUnit timeUnit, BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Optional<Goal> goal = userAnonymized.getGoals().stream().filter(g -> g.getID().equals(goalID)).findAny();
		if (!goal.isPresent())
		{
			throw GoalServiceException.goalNotFoundById(userID, goalID);
		}
		ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(ZoneId.of(userAnonymized.getTimeZoneId()));
		if (!goal.get().wasActiveAtInterval(dateAtStartOfInterval, timeUnit))
		{
			throw ActivityServiceException.activityDateGoalMismatch(userID, date, goalID);
		}
		return inactivityEntitySupplier.apply(goal.get(), dateAtStartOfInterval);
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
