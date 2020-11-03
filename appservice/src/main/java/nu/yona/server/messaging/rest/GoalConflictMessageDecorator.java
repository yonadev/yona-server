/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.analysis.service.GoalConflictMessageDto;
import nu.yona.server.goals.rest.ActivityCategoryController;
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.MessageDto;

@Decorates(GoalConflictMessageDto.class)
public class GoalConflictMessageDecorator implements MessageResourceDecorator
{
	@Override
	public void decorate(MessageResourceAssembler assembler, MessageDto message)
	{
		GoalConflictMessageDto goalConflictMessage = (GoalConflictMessageDto) message;
		addActivityCategoryLink(goalConflictMessage);
		addDayActivityDetailLink(assembler, goalConflictMessage);

	}

	private void addActivityCategoryLink(GoalConflictMessageDto message)
	{
		message.add(ActivityCategoryController.getActivityCategoryLinkBuilder(message.getActivityCategoryId())
				.withRel("activityCategory"));
	}

	private void addDayActivityDetailLink(MessageResourceAssembler assembler, GoalConflictMessageDto message)
	{
		String dateStr = DayActivityDto.formatDate(message.getActivityStartDate());
		if (message.isSentFromBuddy())
		{
			message.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(assembler.getGoalIdMapping().getUserId(),
					assembler.getSenderBuddyId(message), dateStr, message.getGoalId())
					.withRel(BuddyActivityController.DAY_DETAIL_REL));
		}
		else
		{
			message.add(UserActivityController
					.getUserDayActivityDetailLinkBuilder(assembler.getGoalIdMapping().getUserId(), dateStr, message.getGoalId())
					.withRel(UserActivityController.DAY_DETAIL_REL));
		}
	}
}