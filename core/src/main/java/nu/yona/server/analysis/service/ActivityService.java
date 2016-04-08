package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.service.IntervalActivityDTO.LevelOfDetail;
import nu.yona.server.goals.entities.Goal;
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

	@Autowired
	private YonaProperties yonaProperties;

	public Page<WeekActivityOverviewDTO> getWeekActivityOverviews(UUID userID, Pageable pageable)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		StartEndDate startEndDate = getStartEndDate(getCurrentWeekDate(userAnonymized), pageable, ChronoUnit.WEEKS);

		Set<WeekActivity> weekActivityEntities = WeekActivity.getRepository().findAll(userAnonymizedID, startEndDate.startDate,
				startEndDate.endDate);
		Map<LocalDate, Set<WeekActivity>> weekActivityEntitiesByDate = weekActivityEntities.stream()
				.collect(Collectors.groupingBy(a -> a.getDate(), Collectors.toSet()));
		addMissingZeroActivity(weekActivityEntitiesByDate, startEndDate, ChronoUnit.WEEKS, userAnonymized,
				(goal, date) -> createZeroActivityWeek(goal, date));
		return new PageImpl<WeekActivityOverviewDTO>(weekActivityEntitiesByDate.entrySet().stream()
				.map(e -> WeekActivityOverviewDTO.createInstance(e.getValue())).collect(Collectors.toList()), pageable,
				yonaProperties.getAnalysisService().getWeeksActivityMemory());
	}

	private WeekActivity createZeroActivityWeek(Goal goal, ZonedDateTime date)
	{
		// TODO: add days
		return WeekActivity.createInstance(null, goal, date);
	}

	public Page<DayActivityOverviewDTO> getDayActivityOverviews(UUID userID, Pageable pageable)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		StartEndDate startEndDate = getStartEndDate(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Set<DayActivity> dayActivityEntities = DayActivity.getRepository().findAll(userAnonymizedID, startEndDate.startDate,
				startEndDate.endDate);
		Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByDate = dayActivityEntities.stream()
				.collect(Collectors.groupingBy(a -> a.getDate(), Collectors.toSet()));
		addMissingZeroActivity(dayActivityEntitiesByDate, startEndDate, ChronoUnit.DAYS, userAnonymized,
				(goal, date) -> createZeroActivityDay(goal, date));
		return new PageImpl<DayActivityOverviewDTO>(dayActivityEntitiesByDate.entrySet().stream()
				.map(e -> DayActivityOverviewDTO.createInstance(e.getValue())).collect(Collectors.toList()), pageable,
				yonaProperties.getAnalysisService().getDaysActivityMemory());
	}

	private DayActivity createZeroActivityDay(Goal goal, ZonedDateTime date)
	{
		return DayActivity.createInstance(null, goal, date);
	}

	private StartEndDate getStartEndDate(LocalDate currentUnitDate, Pageable pageable, ChronoUnit timeUnit)
	{
		LocalDate dateFrom = currentUnitDate.minus(pageable.getOffset(), timeUnit);
		LocalDate dateUntil = dateFrom.minus(pageable.getPageSize(), timeUnit);
		return new StartEndDate(dateUntil, dateFrom);
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

	private <T extends IntervalActivity> void addMissingZeroActivity(Map<LocalDate, Set<T>> activityEntitiesByDate,
			StartEndDate startEndDate, ChronoUnit timeUnit, UserAnonymizedDTO userAnonymized,
			BiFunction<Goal, ZonedDateTime, T> zeroActivityEntitySupplier)
	{
		for (LocalDate date = startEndDate.startDate; date.isBefore(startEndDate.endDate)
				|| date.isEqual(startEndDate.endDate); date = date.plus(1, timeUnit))
		{
			ZonedDateTime dateAtStartOfDay = date.atStartOfDay(ZoneId.of(userAnonymized.getTimeZoneId()));

			Set<Goal> activeGoals = getActiveGoals(userAnonymized, dateAtStartOfDay);
			if (activeGoals.isEmpty())
			{
				continue;
			}

			if (!activityEntitiesByDate.containsKey(date))
			{
				activityEntitiesByDate.put(date, new HashSet<T>());
			}
			Set<T> activityEntitiesAtDate = activityEntitiesByDate.get(date);
			activeGoals.forEach(g -> addMissingZeroActivity(g, dateAtStartOfDay, activityEntitiesAtDate, userAnonymized,
					zeroActivityEntitySupplier));
		}
	}

	private Set<Goal> getActiveGoals(UserAnonymizedDTO userAnonymized, ZonedDateTime dateAtStartOfDay)
	{
		Set<Goal> activeGoals = userAnonymized.getGoals().stream().filter(g -> g.getCreationTime().isBefore(dateAtStartOfDay))
				.collect(Collectors.toSet());
		return activeGoals;
	}

	private <T extends IntervalActivity> void addMissingZeroActivity(Goal activeGoal, ZonedDateTime dateAtStartOfDay,
			Set<T> activityEntitiesAtDate, UserAnonymizedDTO userAnonymized,
			BiFunction<Goal, ZonedDateTime, T> zeroActivityEntitySupplier)
	{
		if (!containsActivityForGoal(activityEntitiesAtDate, activeGoal))
		{
			activityEntitiesAtDate.add(zeroActivityEntitySupplier.apply(activeGoal, dateAtStartOfDay));
		}
	}

	private <T extends IntervalActivity> boolean containsActivityForGoal(Set<T> dayActivityEntitiesAtDate, Goal goal)
	{
		return dayActivityEntitiesAtDate.stream().anyMatch(a -> a.getGoal().equals(goal));
	}

	public WeekActivityDTO getWeekActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		// TODO: implement for zero activity
		return WeekActivityDTO.createInstance(WeekActivity.getRepository().findOne(userAnonymizedID, date, goalID),
				LevelOfDetail.WeekDetail);
	}

	public DayActivityDTO getDayActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		// TODO: implement for zero activity
		return DayActivityDTO.createInstance(DayActivity.getRepository().findOne(userAnonymizedID, date, goalID),
				LevelOfDetail.DayDetail);
	}

	class StartEndDate
	{
		public final LocalDate startDate;
		public final LocalDate endDate;

		public StartEndDate(LocalDate startDate, LocalDate endDate)
		{
			this.startDate = startDate;
			this.endDate = endDate;
		}
	}
}
