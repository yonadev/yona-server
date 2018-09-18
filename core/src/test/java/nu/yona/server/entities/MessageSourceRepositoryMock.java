/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;

public class MessageSourceRepositoryMock extends MockJpaRepositoryEntityWithUuid<MessageSource> implements MessageSourceRepository
{

}