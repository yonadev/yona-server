/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.MessageDto;

public interface MessageResourceDecorator
{
	void decorate(MessageResourceAssembler assembler, MessageDto message);
}
