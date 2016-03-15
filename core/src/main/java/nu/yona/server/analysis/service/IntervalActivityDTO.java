package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class IntervalActivityDTO
{
	private UUID goalID;
	private ZonedDateTime startTime;

	private List<Integer> spread;
	private int totalActivityDurationMinutes;

	protected IntervalActivityDTO(UUID goalID, ZonedDateTime startTime, List<Integer> spread, int totalActivityDurationMinutes)
	{
		this.goalID = goalID;
		this.startTime = startTime;
		this.spread = spread;
		this.totalActivityDurationMinutes = totalActivityDurationMinutes;
	}

	@JsonIgnore
	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	/*
	 * The ISO-8601 week or day date.
	 */
	public abstract String getDate();

	/*
	 * The time zone in which the interval was recorded.
	 */
	public String getTimeZoneId()
	{
		return startTime.getZone().getId();
	}

	/*
	 * As the goal is already in the user profile, just a reference suffices. Notice that a goal may be removed; in that case it
	 * can be retrieved by the link.
	 */
	@JsonIgnore
	public UUID getGoalID()
	{
		return goalID;
	}

	public List<Integer> getSpread()
	{
		return spread;
	}

	public int getTotalActivityDurationMinutes()
	{
		return totalActivityDurationMinutes;
	}
}
