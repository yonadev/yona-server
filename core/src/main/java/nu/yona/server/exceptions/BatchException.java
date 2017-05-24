/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.io.Serializable;

/**
 * This exception is thrown for various issues that can occur in the batch service.
 */
public class BatchException extends YonaException
{
	private static final long serialVersionUID = 302624164459593737L;

	private BatchException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public static BatchException jobNotFound(String groupName, String jobName)
	{
		return new BatchException("error.batch.job.not.found", groupName, jobName);
	}
}
