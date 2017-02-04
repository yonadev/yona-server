/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.util.TimeUtil;

@JsonRootName("budgetGoal")
public class BudgetGoalDto extends GoalDto
{
	private static final long serialVersionUID = -5887972069171102985L;

	private final int maxDurationMinutes;

	@JsonCreator
	public BudgetGoalDto(
			@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("creationTime") Optional<ZonedDateTime> creationTime,
			@JsonProperty(required = true, value = "maxDurationMinutes") int maxDurationMinutes)
	{
		super(creationTime.map(TimeUtil::toUtcLocalDateTime));

		this.maxDurationMinutes = maxDurationMinutes;
	}

	public BudgetGoalDto(UUID id, UUID activityCategoryId, int maxDurationMinutes, LocalDateTime creationTime,
			Optional<LocalDateTime> endTime, boolean mandatory)
	{
		super(id, Optional.of(creationTime), endTime, activityCategoryId, mandatory);

		this.maxDurationMinutes = maxDurationMinutes;
	}

	@Override
	public String getType()
	{
		return "BudgetGoal";
	}

	@Override
	public void validate()
	{
		if (maxDurationMinutes < 0)
		{
			throw GoalServiceException.budgetGoalMaxDurationNegative(maxDurationMinutes);
		}
	}

	@Override
	public boolean isGoalChanged(Goal existingGoal)
	{
		return ((BudgetGoal) existingGoal).getMaxDurationMinutes() != maxDurationMinutes;
	}

	@Override
	public void updateGoalEntity(Goal existingGoal)
	{
		((BudgetGoal) existingGoal).setMaxDurationMinutes(maxDurationMinutes);
	}

	@Override
	public boolean isNoGoGoal()
	{
		return maxDurationMinutes == 0;
	}

	public int getMaxDurationMinutes()
	{
		return maxDurationMinutes;
	}

	public static BudgetGoalDto createInstance(BudgetGoal entity)
	{
		return new BudgetGoalDto(entity.getId(), entity.getActivityCategory().getId(), entity.getMaxDurationMinutes(),
				entity.getCreationTime(), Optional.ofNullable(entity.getEndTime()), entity.isMandatory());
	}

	@Override
	public BudgetGoal createGoalEntity()
	{
		ActivityCategory activityCategory = ActivityCategory.getRepository().findOne(this.getActivityCategoryId());
		if (activityCategory == null)
		{
			throw ActivityCategoryException.notFound(this.getActivityCategoryId());
		}
		return BudgetGoal.createInstance(getCreationTime().orElse(TimeUtil.utcNow()), activityCategory, this.maxDurationMinutes);
	}
}
