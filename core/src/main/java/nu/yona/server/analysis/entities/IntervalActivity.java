package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.Goal;

@MappedSuperclass
public abstract class IntervalActivity extends EntityWithID
{
	public static final int SPREAD_COUNT = 96;

	@ManyToOne
	private Goal goal;

	/*
	 * The date. Stored for easier querying (if the time zone of the user changes, we cannot query for equal start time).
	 */
	private LocalDate date;

	/*
	 * The start date and time with saved time zone.
	 */
	private ZonedDateTime startTime;

	private int[] spread;
	private int totalActivityDurationMinutes;

	private boolean aggregatesComputed;

	protected IntervalActivity(UUID id, Goal goal, ZonedDateTime startTime, int[] spread, int totalActivityDurationMinutes,
			boolean aggregatesComputed)
	{
		super(id);
		this.goal = goal;
		this.date = startTime.toLocalDate();
		this.startTime = startTime;
		this.spread = spread;
		this.totalActivityDurationMinutes = totalActivityDurationMinutes;
		this.aggregatesComputed = aggregatesComputed;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public LocalDate getDate()
	{
		return date;
	}

	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	public boolean areAggregatesComputed()
	{
		return aggregatesComputed;
	}

	public abstract ZonedDateTime getEndTime();

	public abstract ChronoUnit getTimeUnit();

	public int[] getSpread()
	{
		if (areAggregatesComputed())
		{
			return spread;
		}

		return computeSpread();
	}

	protected abstract int[] computeSpread();

	public int getTotalActivityDurationMinutes()
	{
		if (areAggregatesComputed())
		{
			return totalActivityDurationMinutes;
		}

		return computeTotalActivityDurationMinutes();
	}

	protected abstract int computeTotalActivityDurationMinutes();
}
