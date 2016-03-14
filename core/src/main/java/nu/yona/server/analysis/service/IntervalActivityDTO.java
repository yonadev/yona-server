package nu.yona.server.analysis.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class IntervalActivityDTO
{
	private UUID goalID;
	private ZonedDateTime startTime;

	protected IntervalActivityDTO(UUID goalID, ZonedDateTime startTime)
	{
		this.goalID = goalID;
		this.startTime = startTime;
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
	public ZoneId getZoneId()
	{
		return startTime.getZone();
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
}
