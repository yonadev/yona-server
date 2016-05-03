package nu.yona.server.analysis.service;

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

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalServiceException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class ActivityService
{
	@Autowired
	private UserService userService;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private WeekActivityRepository weekActivityRepository;

	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;

	@Autowired
	private YonaProperties yonaProperties;

	public Page<WeekActivityOverviewDTO> getWeekActivityOverviews(UUID userID, Pageable pageable)
	{
		UUID userAnonymizedID = userService.getUserAnonymizedID(userID);
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentWeekDate(userAnonymized), pageable, ChronoUnit.WEEKS);

		Set<WeekActivity> weekActivityEntities = weekActivityRepository.findAll(userAnonymizedID, interval.startDate,
				interval.endDate);
		Map<LocalDate, Set<WeekActivity>> weekActivityEntitiesByDate = weekActivityEntities.stream()
				.collect(Collectors.groupingBy(a -> a.getDate(), Collectors.toSet()));
		addMissingInactivity(weekActivityEntitiesByDate, interval, ChronoUnit.WEEKS, userAnonymized,
				(goal, startOfWeek) -> WeekActivity.createInstanceInactivity(null, goal, startOfWeek), a -> a.addInactivityDays());
		return new PageImpl<WeekActivityOverviewDTO>(
				weekActivityEntitiesByDate.entrySet().stream()
						.sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> WeekActivityOverviewDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, yonaProperties.getAnalysisService().getWeeksActivityMemory());
	}

	public Page<DayActivityOverviewDTO> getDayActivityOverviews(UUID userID, Pageable pageable)
	{
		UUID userAnonymizedID = userService.getUserAnonymizedID(userID);
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Set<DayActivity> dayActivityEntities = dayActivityRepository.findAll(userAnonymizedID, interval.startDate,
				interval.endDate);
		Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByDate = dayActivityEntities.stream()
				.collect(Collectors.groupingBy(a -> a.getDate(), Collectors.toSet()));
		addMissingInactivity(dayActivityEntitiesByDate, interval, ChronoUnit.DAYS, userAnonymized,
				(goal, startOfDay) -> DayActivity.createInstanceInactivity(null, goal, startOfDay),
				a -> {});
		return new PageImpl<DayActivityOverviewDTO>(
				dayActivityEntitiesByDate.entrySet().stream()
						.sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> DayActivityOverviewDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, yonaProperties.getAnalysisService().getDaysActivityMemory());
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

	public WeekActivityDTO getWeekActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getUserAnonymizedID(userID);
		WeekActivity weekActivityEntity = weekActivityRepository.findOne(userAnonymizedID, date, goalID);
		if (weekActivityEntity == null)
		{
			weekActivityEntity = getMissingInactivity(userID, date, goalID, userAnonymizedID, ChronoUnit.WEEKS,
					(goal, startOfWeek) -> WeekActivity.createInstanceInactivity(null, goal, startOfWeek));
		}
		return WeekActivityDTO.createInstance(weekActivityEntity, LevelOfDetail.WeekDetail);
	}

	public DayActivityDTO getDayActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getUserAnonymizedID(userID);
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
