/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class GoalChangeMessage extends BuddyMessage
{
	public enum Change
	{
		GOAL_ADDED, GOAL_DELETED, GOAL_CHANGED
	}

	@ManyToOne
	private ActivityCategory activityCategoryOfChangedGoal;

	@JdbcType(value = IntegerJdbcType.class)
	@Column(name = "`change`") // "change" is a reserved word for MySQL
	private Change change;

	// Default constructor is required for JPA
	protected GoalChangeMessage()
	{
		super();
	}

	protected GoalChangeMessage(BuddyInfoParameters buddyInfoParameters, ActivityCategory activityCategoryOfChangedGoal,
			Change change, String message)
	{
		super(buddyInfoParameters, message);

		this.activityCategoryOfChangedGoal = activityCategoryOfChangedGoal;
		this.change = change;
	}

	public static GoalChangeMessage createInstance(BuddyInfoParameters buddyInfoParameters,
			ActivityCategory activityCategoryOfChangedGoal, Change change, String message)
	{
		return new GoalChangeMessage(buddyInfoParameters, activityCategoryOfChangedGoal, change, message);
	}

	public ActivityCategory getActivityCategoryOfChangedGoal()
	{
		return activityCategoryOfChangedGoal;
	}

	public Change getChange()
	{
		return change;
	}
}
