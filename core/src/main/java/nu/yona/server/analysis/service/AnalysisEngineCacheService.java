/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.messaging.entities.MessageDestination;

@Service
public class AnalysisEngineCacheService
{
	@Cacheable(value = "goalConflictMessages", key = "{#userAnonymizedID,#goalID,#destination.ID}")
	public GoalConflictMessage fetchLatestGoalConflictMessageForUser(UUID userAnonymizedID, UUID goalID,
			MessageDestination destination, Date minEndTime)
	{
		List<GoalConflictMessage> results = GoalConflictMessage.getGoalConflictMessageRepository()
				.findLatestGoalConflictMessageFromDestination(userAnonymizedID, goalID, destination.getID(), minEndTime,
						GoalConflictMessage.class);

		return results != null && !results.isEmpty() ? results.get(0) : null;
	}

	@CachePut(value = "goalConflictMessages", key = "{#message.relatedUserAnonymizedID,#message.goalID,#destination.ID}")
	public GoalConflictMessage updateLatestGoalConflictMessageForUser(GoalConflictMessage message, MessageDestination destination)
	{
		// This will save the message as it has already been added to the destination. The message is passed
		// to this method in order to update the cache properly after the update.
		MessageDestination.getRepository().save(destination);

		return message;
	}
}
