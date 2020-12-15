/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.DisclosureResponseMessageDto;
import nu.yona.server.messaging.service.MessageDto;

@Decorates(DisclosureResponseMessageDto.class)
public class DisclosureResponseMessageDecorator implements MessageResourceDecorator
{
	@Override
	public void decorate(MessageResourceAssembler assembler, MessageDto message)
	{
		DisclosureResponseMessageDto disclosureResponseMessage = (DisclosureResponseMessageDto) message;
		if (disclosureResponseMessage.getStatus() == GoalConflictMessage.Status.DISCLOSURE_ACCEPTED)
		{
			addDayActivityDetailLink(assembler, disclosureResponseMessage);
		}
	}

	private void addDayActivityDetailLink(MessageResourceAssembler assembler, DisclosureResponseMessageDto message)
	{
		String dateStr = DayActivityDto.formatDate(message.getGoalConflictStartTime());
		message.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(assembler.getGoalIdMapping().getUserId(),
				assembler.getSenderBuddyId(message), dateStr, message.getGoalId())
				.withRel(BuddyActivityController.DAY_DETAIL_REL));
	}
}