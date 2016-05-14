/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class GoalChangeMessage extends BuddyMessage
{
	public enum Change
	{
		GOAL_ADDED, GOAL_DELETED, GOAL_CHANGED
	}

	@ManyToOne
	private Goal changedGoal;

	private Change change;

	// Default constructor is required for JPA
	protected GoalChangeMessage()
	{
		super();
	}

	protected GoalChangeMessage(UUID id, UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname, Goal changedGoal, Change change,
			String message)
	{
		super(id, senderUserID, senderUserAnonymizedID, senderNickname, message);

		this.changedGoal = changedGoal;
		this.change = change;
	}

	public Goal getChangedGoal()
	{
		return changedGoal;
	}

	public Change getChange()
	{
		return change;
	}

	public static GoalChangeMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname, Goal changedGoal,
			Change change, String message)
	{
		return new GoalChangeMessage(UUID.randomUUID(), senderUserID, senderUserAnonymizedID, senderNickname, changedGoal, change, message);
	}
}
