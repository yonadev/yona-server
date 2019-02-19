/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.io.Serializable;

import nu.yona.server.exceptions.YonaException;

public class FirebaseServiceException extends YonaException
{
	private static final long serialVersionUID = 8840294619248574727L;

	private FirebaseServiceException(Throwable t, String messageId, Serializable... parameters)
	{
		super(t, messageId, parameters);
	}

	public static FirebaseServiceException couldNotSendMessage(Throwable t)
	{
		return new FirebaseServiceException(t, "error.firebase.could.not.send.message");
	}
}
