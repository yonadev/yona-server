/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import java.util.Objects;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.MessageDto;

@Decorates(ActivityCommentMessageDto.class)
public class ActivityCommentMessageDecorator implements MessageResourceDecorator
{
	@Override
	public void decorate(MessageResourceAssembler assembler, MessageDto message)
	{
		ActivityCommentMessageDto activityCommentMessage = (ActivityCommentMessageDto) message;
		IntervalActivity activity = getIntervalActivity(message, activityCommentMessage);
		Goal goal = activity.getGoal();
		assertGoalNotNull(goal, activity);
		if (assembler.getGoalIdMapping().isUserGoal(goal.getId()))
		{
			assembler.getMessageController().getUserActivityController().addLinks(assembler.getGoalIdMapping(), activity,
					activityCommentMessage);
		}
		else
		{
			assembler.getMessageController().getBuddyActivityController().addLinks(assembler.getGoalIdMapping(), activity,
					activityCommentMessage);
		}
	}

	private void assertGoalNotNull(Goal goal, IntervalActivity activity)
	{
		Objects.requireNonNull(goal,
				String.format("Activity getGoal() returns null for '%s' instance with id '%s' and start time '%s'",
						activity.getClass().getSimpleName(), activity.getId(), activity.getStartDate()));
	}

	private IntervalActivity getIntervalActivity(MessageDto message, ActivityCommentMessageDto activityCommentMessageMessage)
	{
		return IntervalActivity.getIntervalActivityRepository().findById(activityCommentMessageMessage.getIntervalActivityId())
				.orElseThrow(() -> new IllegalStateException(String.format(
						"Activity linked from activity comment message not found from sender '%s' and activity id '%s'",
						message.getSenderNickname(), activityCommentMessageMessage.getIntervalActivityId())));
	}
}