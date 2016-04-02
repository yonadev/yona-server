package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
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
	private final static DateTimeFormatter ISO8601_DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private boolean goalAccomplished;
	private int totalMinutesBeyondGoal;
	private Set<MessageDTO> messages;

	private DayActivityDTO(UUID goalID, ZonedDateTime startTime, List<Integer> spread, Integer totalActivityDurationMinutes,
			boolean goalAccomplished, int totalMinutesBeyondGoal, Set<MessageDTO> messages)
	{
		super(goalID, startTime, spread, totalActivityDurationMinutes);
		this.goalAccomplished = goalAccomplished;
		this.totalMinutesBeyondGoal = totalMinutesBeyondGoal;
		this.messages = messages;
	}

	@Override
	public String getDate()
	{
		return getStartTime().toLocalDate().format(ISO8601_DAY_FORMATTER);
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

	static DayActivityDTO createInstance(DayActivity dayActivity, LevelOfDetail levelOfDetail)
	{
		// TODO: fetch related messages
		return new DayActivityDTO(dayActivity.getGoal().getID(), dayActivity.getStartTime(),
				includeIf(() -> dayActivity.getSpread(),
						levelOfDetail == LevelOfDetail.DayDetail
								|| levelOfDetail == LevelOfDetail.DayOverview && dayActivity.getGoal() instanceof TimeZoneGoal),
				dayActivity.getTotalActivityDurationMinutes(), dayActivity.isGoalAccomplished(),
				dayActivity.getTotalMinutesBeyondGoal(),
				includeIf(() -> new HashSet<MessageDTO>(), levelOfDetail == LevelOfDetail.DayDetail));
	}
}
