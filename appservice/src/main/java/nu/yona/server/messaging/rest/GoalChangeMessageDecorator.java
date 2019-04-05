/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import nu.yona.server.goals.rest.ActivityCategoryController;
import nu.yona.server.goals.service.GoalChangeMessageDto;
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.MessageDto;

@Decorates(GoalChangeMessageDto.class)
public class GoalChangeMessageDecorator implements Decorator
{
	@Override
	public void decorate(MessageResourceAssembler assembler, MessageDto message)
	{
		GoalChangeMessageDto goalChangeMessage = (GoalChangeMessageDto) message;
		message.add(ActivityCategoryController
				.getActivityCategoryLinkBuilder(goalChangeMessage.getActivityCategoryIdOfChangedGoal()).withRel("related"));
	}
}