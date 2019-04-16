/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.rest.UserController.UserResourceAssembler;
import nu.yona.server.subscriptions.service.BuddyDto;

@Decorates(BuddyMessageEmbeddedUserDto.class)
public class BuddyMessageEmbeddedUserDecorator implements MessageResourceDecorator
{
	@Override
	public void decorate(MessageResourceAssembler assembler, MessageDto message)
	{
		BuddyMessageEmbeddedUserDto buddyMessage = (BuddyMessageEmbeddedUserDto) message;
		message.getSenderUser()
				.ifPresent(user -> buddyMessage.setEmbeddedUser(
						assembler.getCurieProvider().getNamespacedRelFor(BuddyDto.USER_REL_NAME),
						createUserResourceAssembler(assembler).toResource(user)));

	}

	private UserResourceAssembler createUserResourceAssembler(MessageResourceAssembler assembler)
	{
		return UserController.UserResourceAssembler.createMinimalInstance(assembler.getCurieProvider(),
				assembler.getGoalIdMapping().getUserId());
	}
}