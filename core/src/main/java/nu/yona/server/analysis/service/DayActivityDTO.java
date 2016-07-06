package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.messaging.service.MessageDTO;

@JsonRootName("dayActivity")
public class DayActivityDTO extends IntervalActivityDTO
{
	private static final DateTimeFormatter ISO8601_DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final boolean goalAccomplished;
	private final int totalMinutesBeyondGoal;
	private final Set<MessageDTO> messages;

	private DayActivityDTO(UUID goalID, ZonedDateTime startTime, boolean shouldSerializeDate, List<Integer> spread,
			Optional<Integer> totalActivityDurationMinutes, boolean goalAccomplished, int totalMinutesBeyondGoal,
			Set<MessageDTO> messages, boolean hasPrevious, boolean hasNext)
	{
		super(goalID, startTime, shouldSerializeDate, spread, totalActivityDurationMinutes, hasPrevious, hasNext);
		this.goalAccomplished = goalAccomplished;
		this.totalMinutesBeyondGoal = totalMinutesBeyondGoal;
		this.messages = messages;
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.DAYS;
	}

	@Override
	protected String formatDateAsISO(LocalDate date)
	{
		return formatDate(date);
	}

	public boolean isGoalAccomplished()
	{
		return goalAccomplished;
	}

	public int getTotalMinutesBeyondGoal()
	{
		return totalMinutesBeyondGoal;
	}

	@JsonIgnore
	public Set<MessageDTO> getMessages()
	{
		return messages;
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, ISO8601_DAY_FORMATTER);
	}

	public static String formatDate(LocalDate date)
	{
		return date.format(ISO8601_DAY_FORMATTER);
	}

	static DayActivityDTO createInstance(DayActivity dayActivity, LevelOfDetail levelOfDetail)
	{
		return new DayActivityDTO(dayActivity.getGoal().getID(), dayActivity.getStartTime(),
				levelOfDetail == LevelOfDetail.DayDetail, getSpread(dayActivity, levelOfDetail),
				Optional.of(dayActivity.getTotalActivityDurationMinutes()), dayActivity.isGoalAccomplished(),
				dayActivity.getTotalMinutesBeyondGoal(),
				levelOfDetail == LevelOfDetail.DayDetail ? getMessages(dayActivity) : Collections.emptySet(),
				dayActivity.hasPrevious(), dayActivity.hasNext());
	}

	private static List<Integer> getSpread(DayActivity dayActivity, LevelOfDetail levelOfDetail)
	{
		return includeSpread(dayActivity, levelOfDetail) ? dayActivity.getSpread() : Collections.emptyList();
	}

	private static Set<MessageDTO> getMessages(DayActivity dayActivity)
	{
		// TODO: fetch related messages here
		return Collections.emptySet();
	}

	private static boolean includeSpread(DayActivity dayActivity, LevelOfDetail levelOfDetail)
	{
		return levelOfDetail == LevelOfDetail.DayDetail
				|| levelOfDetail == LevelOfDetail.DayOverview && dayActivity.getGoal() instanceof TimeZoneGoal;
	}
}
