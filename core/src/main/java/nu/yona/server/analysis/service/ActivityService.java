package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
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
		return new PageImpl<WeekActivityOverviewDTO>(weekActivityEntitiesByDate.entrySet().stream()
				.map(e -> WeekActivityOverviewDTO.createInstance(e.getValue())).collect(Collectors.toList()), pageable,
				yonaProperties.getAnalysisService().getWeeksActivityMemory());
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
		addMissingZeroActivity(dayActivityEntitiesByDate, startEndDate, ChronoUnit.DAYS, userAnonymized);
		return new PageImpl<DayActivityOverviewDTO>(dayActivityEntitiesByDate.entrySet().stream()
				.map(e -> DayActivityOverviewDTO.createInstance(e.getValue())).collect(Collectors.toList()), pageable,
				yonaProperties.getAnalysisService().getDaysActivityMemory());
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

	private void addMissingZeroActivity(Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByDate, StartEndDate startEndDate,
			ChronoUnit timeUnit, UserAnonymizedDTO userAnonymized)
	{
		Set<Goal> currentGoals = userAnonymized.getGoals();
		for (LocalDate date = startEndDate.startDate; date.isBefore(startEndDate.endDate)
				|| date.isEqual(startEndDate.endDate); date = date.plus(1, timeUnit))
		{
			if (!dayActivityEntitiesByDate.containsKey(date))
			{
				// TODO: check if any goals present (goal creation time)
				dayActivityEntitiesByDate.put(date, new HashSet<DayActivity>());
			}

			Set<DayActivity> dayActivityEntitiesAtDate = dayActivityEntitiesByDate.get(date);
			addMissingZeroActivity(currentGoals, date, dayActivityEntitiesAtDate, userAnonymized);
		}
	}

	private void addMissingZeroActivity(Set<Goal> currentGoals, LocalDate date, Set<DayActivity> dayActivityEntitiesAtDate,
			UserAnonymizedDTO userAnonymized)
	{
		currentGoals.forEach(g -> addMissingZeroActivity(g, date, dayActivityEntitiesAtDate, userAnonymized));
	}

	private void addMissingZeroActivity(Goal currentGoal, LocalDate date, Set<DayActivity> dayActivityEntitiesAtDate,
			UserAnonymizedDTO userAnonymized)
	{
		// TODO: check goal creation time
		if (!containsActivityForGoal(dayActivityEntitiesAtDate, currentGoal))
		{
			dayActivityEntitiesAtDate.add(
					DayActivity.createInstance(null, currentGoal, date.atStartOfDay(ZoneId.of(userAnonymized.getTimeZoneId()))));
		}
	}

	private boolean containsActivityForGoal(Set<DayActivity> dayActivityEntitiesAtDate, Goal goal)
	{
		return dayActivityEntitiesAtDate.stream().anyMatch(a -> a.getGoal().equals(goal));
	}

	public WeekActivityDTO getWeekActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		return WeekActivityDTO.createInstance(WeekActivity.getRepository().findOne(userAnonymizedID, date, goalID),
				LevelOfDetail.WeekDetail);
	}

	public DayActivityDTO getDayActivityDetail(UUID userID, LocalDate date, UUID goalID)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
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
