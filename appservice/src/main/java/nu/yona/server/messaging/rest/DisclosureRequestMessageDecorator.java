/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.DisclosureRequestMessageDto;
import nu.yona.server.messaging.service.MessageDto;

@Decorates(DisclosureRequestMessageDto.class)
public class DisclosureRequestMessageDecorator implements MessageResourceDecorator
{
	@Override
	public void decorate(MessageResourceAssembler assembler, MessageDto message)
	{
		DisclosureRequestMessageDto disclosureRequestMessage = (DisclosureRequestMessageDto) message;
		String dateStr = DayActivityDto.formatDate(disclosureRequestMessage.getGoalConflictStartTime());
		message.add(UserActivityController.getUserDayActivityDetailLinkBuilder(assembler.getGoalIdMapping().getUserId(), dateStr,
				disclosureRequestMessage.getGoalId()).withRel(UserActivityController.DAY_DETAIL_REL));
	}
}