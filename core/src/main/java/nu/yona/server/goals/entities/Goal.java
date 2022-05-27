/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Where;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.entities.EntityUtil;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "GOALS")
public abstract class Goal extends EntityWithUuid implements IGoal
{
	protected static final LocalDateTime mandatoryGoalPresetCreationTime = LocalDateTime.of(2017, 1, 1, 12, 0);

	@ManyToOne
	@JoinColumn(name = "user_anonymized_id")
	private UserAnonymized userAnonymized;

	@ManyToOne
	private ActivityCategory activityCategory;

	private LocalDateTime creationTime;
	private LocalDateTime endTime;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Goal previousInstanceOfThisGoal;

	@OneToMany(cascade = CascadeType.ALL)
	@JoinColumn(name = "goal_id", referencedColumnName = "id")
	@Where(clause = "dtype='WeekActivity'")
	private final List<WeekActivity> weekActivities = new ArrayList<>();

	// Default constructor is required for JPA
	public Goal()
	{
		super(null);
	}

	protected Goal(UUID id, LocalDateTime creationTime, ActivityCategory activityCategory)
	{
		super(id);

		if (activityCategory == null)
		{
			throw new IllegalArgumentException("activityCategory cannot be null");
		}

		this.activityCategory = activityCategory;

		if (this.isMandatory())
		{
			this.creationTime = mandatoryGoalPresetCreationTime;
		}
		else
		{
			this.creationTime = creationTime;
		}
	}

	protected Goal(UUID id, Goal originalGoal, LocalDateTime endTime)
	{
		super(id);

		if (originalGoal == null)
		{
			throw new IllegalArgumentException("originalGoal cannot be null");
		}
		this.userAnonymized = originalGoal.userAnonymized;
		this.activityCategory = originalGoal.activityCategory;
		this.creationTime = originalGoal.creationTime;
		this.endTime = endTime;
		this.previousInstanceOfThisGoal = originalGoal.previousInstanceOfThisGoal;
	}

	public static GoalRepository getRepository()
	{
		return (GoalRepository) RepositoryProvider.getRepository(Goal.class, UUID.class);
	}

	public void setUserAnonymized(UserAnonymized userAnonymized)
	{
		this.userAnonymized = userAnonymized;
	}

	public ActivityCategory getActivityCategory()
	{
		return activityCategory;
	}

	public LocalDateTime getCreationTime()
	{
		return creationTime;
	}

	@Override
	public LocalDateTime getCreationTimeNonOptional()
	{
		return getCreationTime();
	}

	/**
	 * For test purposes only.
	 */
	public void setCreationTime(LocalDateTime creationTime)
	{
		this.creationTime = creationTime;
	}

	@Override
	public Optional<LocalDateTime> getEndTime()
	{
		return Optional.ofNullable(endTime);
	}

	public Optional<Goal> getPreviousVersionOfThisGoal()
	{
		if (previousInstanceOfThisGoal == null)
		{
			return Optional.empty();
		}
		return Optional.of(EntityUtil.enforceLoading(previousInstanceOfThisGoal));
	}

	public void setPreviousVersionOfThisGoal(Goal previousGoal)
	{
		this.previousInstanceOfThisGoal = previousGoal;
	}

	public List<WeekActivity> getWeekActivities()
	{
		return Collections.unmodifiableList(weekActivities);
	}

	public void addWeekActivity(WeekActivity weekActivity)
	{

		weekActivities.add(Objects.requireNonNull(weekActivity));
	}

	public void setWeekActivities(List<WeekActivity> weekActivities)
	{
		this.weekActivities.clear();
		this.weekActivities.addAll(weekActivities);
		weekActivities.forEach(wa -> wa.setGoal(this));
	}

	public void transferHistoryActivities(Goal historyGoal)
	{
		transferHistoryWeekActivities(historyGoal);

		setHistoryGoalForHistoryRemainingDayActivities(historyGoal);
	}

	private void setHistoryGoalForHistoryRemainingDayActivities(Goal historyGoal)
	{
		weekActivities.forEach(wa -> wa.getDayActivities().stream()
				.filter(da -> historyGoal.wasActiveAtInterval(da.getStartTime(), ChronoUnit.DAYS))
				.forEach(da -> da.setGoal(historyGoal)));
	}

	private void transferHistoryWeekActivities(Goal historyGoal)
	{
		List<WeekActivity> historyWeekActivities = weekActivities.stream()
				.filter(wa -> historyGoal.wasActiveAtInterval(wa.getStartTime(), ChronoUnit.WEEKS)).collect(Collectors.toList());
		historyGoal.setWeekActivities(historyWeekActivities);
		weekActivities.removeAll(historyWeekActivities);
	}

	public void removeAllWeekActivities()
	{
		WeekActivityRepository repository = WeekActivity.getRepository();
		repository.deleteAll(weekActivities);
		weekActivities.clear();
	}

	public abstract Goal cloneAsHistoryItem(LocalDateTime endTime);

	public boolean isMandatory()
	{
		return getActivityCategory().isMandatoryNoGo();
	}

	@Override
	public abstract boolean isGoalAccomplished(DayActivity dayActivity);

	@Override
	public abstract int computeTotalMinutesBeyondGoal(DayActivity dayActivity);

	public Set<UUID> getIdsIncludingHistoryItems()
	{
		Set<UUID> ids = new HashSet<>();
		Optional<Goal> previous = Optional.of(this);
		while (previous.isPresent())
		{
			ids.add(previous.get().getId());
			previous = previous.get().getPreviousVersionOfThisGoal();
		}

		return ids;
	}
}
