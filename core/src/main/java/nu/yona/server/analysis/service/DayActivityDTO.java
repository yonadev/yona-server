package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.messaging.service.MessageDTO;

@JsonRootName("dayActivity")
public class DayActivityDTO extends IntervalActivityDTO
{
	private final static String ISO8601_DAY_FORMAT = "yyyy-MM-dd";

	private boolean goalAccomplished;
	private Set<MessageDTO> messages;

	private DayActivityDTO(UUID goalID, ZonedDateTime startTime, List<Integer> spread, int totalActivityDurationMinutes,
			boolean goalAccomplished, Set<MessageDTO> messages)
	{
		super(goalID, startTime, spread, totalActivityDurationMinutes);
		this.goalAccomplished = goalAccomplished;
		this.messages = messages;
	}

	@Override
	public String getDate()
	{
		return getStartTime().toLocalDate().format(DateTimeFormatter.ofPattern(ISO8601_DAY_FORMAT));
	}

	public boolean isGoalAccomplished()
	{
		return goalAccomplished;
	}

	@JsonIgnore
	public Set<MessageDTO> getMessages()
	{
		return messages;
	}

	public static LocalDate parseDate(String iso8601)
	{
		return LocalDate.parse(iso8601, DateTimeFormatter.ofPattern(ISO8601_DAY_FORMAT));
	}

	static DayActivityDTO createInstance(DayActivity dayActivity)
	{
		// TODO: fetch related messages
		return new DayActivityDTO(dayActivity.getGoal().getID(), dayActivity.getStartTime(), dayActivity.getSpread(),
				dayActivity.getTotalActivityDurationMinutes(), dayActivity.isGoalAccomplished(), null);
	}
}
