package nu.yona.server.goals.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.analysis.entities.DayActivity;

@Entity
public class TimeZoneGoal extends Goal
{
	private String[] zones;

	// Default constructor is required for JPA
	public TimeZoneGoal()
	{

	}

	private TimeZoneGoal(UUID id, ActivityCategory activityCategory, String[] zones)
	{
		super(id, activityCategory);

		this.zones = zones;
	}

	public String[] getZones()
	{
		return zones;
	}

	public static TimeZoneGoal createInstance(ActivityCategory activityCategory, String[] zones)
	{
		return new TimeZoneGoal(UUID.randomUUID(), activityCategory, zones);
	}

	@Override
	public boolean isMandatory()
	{
		return false;
	}

	@Override
	public boolean isNoGoGoal()
	{
		return false;
	}

	@Override
	public boolean isGoalAccomplished(DayActivity dayActivity)
	{
		// TODO
		return true;
	}
}
