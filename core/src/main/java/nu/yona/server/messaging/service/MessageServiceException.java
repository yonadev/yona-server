/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.Message;

public class MessageServiceException extends YonaException
{
	private static final long serialVersionUID = -4705914008854129434L;

	private MessageServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static MessageServiceException noDtoManagerRegistered(Class<? extends Message> clz)
	{
		return new MessageServiceException("error.message.no.dto.manager.registered", clz);
	}

	public static MessageServiceException actionNotSupported(String action)
	{
		return new MessageServiceException("error.message.action.not.supported", action);
	}

	public static MessageServiceException missingMandatoryActionProperty(String action, String propertyName)
	{
		return new MessageServiceException("error.message.action.missing.property", action, propertyName);
	}
}
