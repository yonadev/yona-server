package nu.yona.server.analysis.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
				(goal, startOfWeek) -> createAndSaveWeekInactivity(userAnonymizedID, goal, startOfWeek),
				a -> a.addInactivityDaysIfNeeded());
		Map<ZonedDateTime, Set<WeekActivity>> weekActivityEntitiesByZonedDate = mapToZonedDateTime(
				weekActivityEntitiesByLocalDate);
		return new PageImpl<WeekActivityOverviewDTO>(
				weekActivityEntitiesByZonedDate.entrySet().stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
						.map(e -> WeekActivityOverviewDTO.createInstance(e.getKey(), e.getValue())).collect(Collectors.toList()),
				pageable, getTotalPageableItems(userAnonymized, ChronoUnit.WEEKS));
	}

	private WeekActivity createAndSaveWeekInactivity(UUID userAnonymizedID, Goal goal, ZonedDateTime startOfWeek)
	{
		return WeekActivity.getRepository().save(WeekActivity
				.createInstanceInactivity(userAnonymizedService.getUserAnonymizedEntity(userAnonymizedID), goal, startOfWeek));
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
	public Page<DayActivityOverviewDTO<DayActivityDTO>> getUserDayActivityOverviews(UUID userID, Pageable pageable)
	{
		return getDayActivityOverviews(userService.getUserAnonymizedID(userID), pageable);
	}

	@Transactional
	public Page<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> getUserDayActivityOverviewsWithBuddies(UUID userID,
			Pageable pageable)
	{
		UUID userAnonymizedID = userService.getUserAnonymizedID(userID);
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Set<BuddyDTO> buddies = buddyService.getBuddiesOfUser(userID);
		Set<UUID> userAnonymizedIDs = buddies.stream().map(b -> b.getUserAnonymizedID()).collect(Collectors.toSet());
		userAnonymizedIDs.add(userAnonymizedID);
		Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate = userAnonymizedIDs.stream()
				.map(id -> getDayActivities(userAnonymizedService.getUserAnonymized(id), interval)).map(Map::entrySet)
				.flatMap(Collection::stream).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
					Set<DayActivity> allActivities = new HashSet<>(a);
					allActivities.addAll(b);
					return allActivities;
				}));
		List<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> dayActivityOverviews = dayActivityEntitiesToOverviewsUserWithBuddies(
				dayActivityEntitiesByZonedDate);
		return new PageImpl<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>(dayActivityOverviews, pageable,
				getTotalPageableItems(userAnonymized, ChronoUnit.DAYS));
	}

	@Transactional
	public Page<DayActivityOverviewDTO<DayActivityDTO>> getBuddyDayActivityOverviews(UUID buddyID, Pageable pageable)
	{
		return getDayActivityOverviews(buddyService.getBuddy(buddyID).getUserAnonymizedID(), pageable);
	}

	private Page<DayActivityOverviewDTO<DayActivityDTO>> getDayActivityOverviews(UUID userAnonymizedID, Pageable pageable)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Interval interval = getInterval(getCurrentDayDate(userAnonymized), pageable, ChronoUnit.DAYS);

		Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate = getDayActivities(userAnonymized, interval);
		List<DayActivityOverviewDTO<DayActivityDTO>> dayActivityOverviews = dayActivityEntitiesToOverviews(
				dayActivityEntitiesByZonedDate);

		return new PageImpl<DayActivityOverviewDTO<DayActivityDTO>>(dayActivityOverviews, pageable,
				getTotalPageableItems(userAnonymized, ChronoUnit.DAYS));
	}

	private Map<ZonedDateTime, Set<DayActivity>> getDayActivities(UserAnonymizedDTO userAnonymized, Interval interval)
	{
		Map<LocalDate, Set<DayActivity>> dayActivityEntitiesByLocalDate = getDayActivitiesGroupedByDate(userAnonymized.getID(),
				interval);
		addMissingInactivity(dayActivityEntitiesByLocalDate, interval, ChronoUnit.DAYS, userAnonymized,
				(goal, startOfDay) -> createAndSaveDayInactivity(userAnonymized.getID(), goal, startOfDay), a -> {
				});
		Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate = mapToZonedDateTime(dayActivityEntitiesByLocalDate);
		return dayActivityEntitiesByZonedDate;
	}

	private DayActivity createAndSaveDayInactivity(UUID userAnonymizedID, Goal goal, ZonedDateTime startOfDay)
	{
		return DayActivity.getRepository().save(DayActivity
				.createInstanceInactivity(userAnonymizedService.getUserAnonymizedEntity(userAnonymizedID), goal, startOfDay));
	}

	private List<DayActivityOverviewDTO<DayActivityDTO>> dayActivityEntitiesToOverviews(
			Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate)
	{
		List<DayActivityOverviewDTO<DayActivityDTO>> dayActivityOverviews = dayActivityEntitiesByZonedDate.entrySet().stream()
				.sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
				.map(e -> DayActivityOverviewDTO.createInstanceForUser(e.getKey(), e.getValue())).collect(Collectors.toList());
		return dayActivityOverviews;
	}

	private List<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> dayActivityEntitiesToOverviewsUserWithBuddies(
			Map<ZonedDateTime, Set<DayActivity>> dayActivityEntitiesByZonedDate)
	{
		List<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> dayActivityOverviews = dayActivityEntitiesByZonedDate.entrySet()
				.stream().sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
				.map(e -> DayActivityOverviewDTO.createInstanceForUserWithBuddies(e.getKey(), e.getValue()))
				.collect(Collectors.toList());
		return dayActivityOverviews;
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

			Set<GoalDTO> activeGoals = getActiveGoals(userAnonymized, dateAtStartOfInterval, timeUnit);
			if (activeGoals.isEmpty())
			{
				continue;
			}

			if (!activityEntitiesByDate.containsKey(date))
			{
				activityEntitiesByDate.put(date, new HashSet<T>());
			}
			Set<T> activityEntitiesAtDate = activityEntitiesByDate.get(date);
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
					(goal, startOfDay) -> createAndSaveDayInactivity(userAnonymizedID, goal, startOfDay));
		}
		return DayActivityDTO.createInstance(dayActivityEntity, LevelOfDetail.DayDetail);
	}

	@Transactional
	public Page<MessageDTO> getUserDayActivityDetailMessages(UUID userID, LocalDate date, UUID goalID, Pageable pageable)
	{
		return getDayActivityDetailMessages(userID, userService.getUserAnonymizedID(userID), date, goalID, pageable);
	}

	@Transactional
	public Page<MessageDTO> getBuddyDayActivityDetailMessages(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			Pageable pageable)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		return getDayActivityDetailMessages(userID, buddy.getUserAnonymizedID(), date, goalID, pageable);
	}

	private Page<MessageDTO> getDayActivityDetailMessages(UUID userID, UUID userAnonymizedID, LocalDate date, UUID goalID,
			Pageable pageable)
	{
		DayActivity dayActivityEntity = dayActivityRepository.findOne(userAnonymizedID, date, goalID);
		if (dayActivityEntity == null)
		{
			return new PageImpl<>(Collections.emptyList());
		}
		return messageService.getActivityRelatedMessages(userID, dayActivityEntity.getID(), pageable);
	}

	@Transactional
	public MessageDTO addMessageToDayActivity(UUID userID, UUID buddyID, LocalDate date, UUID goalID,
			PostPutActivityCommentMessageDTO message)
	{
		UserDTO sendingUser = userService.getPrivateUser(userID);
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		DayActivity dayActivityEntity = dayActivityRepository.findOne(buddy.getUserAnonymizedID(), date, goalID);
		if (dayActivityEntity == null)
		{
			throw ActivityServiceException.buddyDayActivityNotFound(userID, buddyID, date, goalID);
		}

		return sendMessagePair(sendingUser, buddy.getUserAnonymizedID(), dayActivityEntity.getID(), Optional.empty(),
				Optional.empty(), message.getMessage());
	}

	private MessageDTO sendMessagePair(UserDTO sendingUser, UUID targetUserAnonymizedID, UUID activityID,
			Optional<ActivityCommentMessage> repliedMessageOfSelf, Optional<ActivityCommentMessage> repliedMessageOfBuddy,
			String message)
	{
		ActivityCommentMessage messageToBuddy = createMessage(sendingUser, activityID,
				repliedMessageOfBuddy.map(ActivityCommentMessage::getID), message);
		ActivityCommentMessage messageToSelf = createMessage(sendingUser, activityID,
				repliedMessageOfSelf.map(ActivityCommentMessage::getID), message);
		sendMessage(sendingUser.getPrivateData().getUserAnonymizedID(), messageToSelf);
		messageToBuddy.setSenderCopyMessage(messageToSelf);
		sendMessage(targetUserAnonymizedID, messageToBuddy);

		return messageService.messageToDTO(sendingUser, messageToSelf);
	}

	private void sendMessage(UUID targetUserAnonymizedID, ActivityCommentMessage messageEntity)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(targetUserAnonymizedID);
		messageService.sendMessageToUserAnonymized(userAnonymized, messageEntity);
	}

	private ActivityCommentMessage createMessage(UserDTO sendingUser, UUID activityID, Optional<UUID> repliedMessageID,
			String message)
	{
		ActivityCommentMessage messageEntity = ActivityCommentMessage.createInstance(sendingUser.getID(),
				sendingUser.getPrivateData().getUserAnonymizedID(), sendingUser.getPrivateData().getNickname(), activityID,
				message, repliedMessageID);
		return messageEntity;
	}

	private <T extends IntervalActivity> T getMissingInactivity(UUID userID, LocalDate date, UUID goalID, UUID userAnonymizedID,
			ChronoUnit timeUnit, BiFunction<Goal, ZonedDateTime, T> inactivityEntitySupplier)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Goal goal = goalService.getGoalEntityForUserAnonymizedID(userAnonymized.getID(), goalID);
		ZonedDateTime dateAtStartOfInterval = date.atStartOfDay(ZoneId.of(userAnonymized.getTimeZoneId()));
		if (!goal.wasActiveAtInterval(dateAtStartOfInterval, timeUnit))
		{
			throw ActivityServiceException.activityDateGoalMismatch(userID, date, goalID);
		}
		return inactivityEntitySupplier.apply(goal, dateAtStartOfInterval);
	}

	@Transactional
	public MessageDTO replyToMessage(UserDTO sendingUser, ActivityCommentMessage repliedMessage, String message)
	{
		UUID targetUserAnonymizedID = repliedMessage.getRelatedUserAnonymizedID();
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
