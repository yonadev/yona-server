/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * This exception is to be used in case data is wrong in DTOs. So whenever a field has a wrong value you should throw this
 * exception.
 * 
 * @author pgussow
 */
public class AnalysisException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private AnalysisException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private AnalysisException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static AnalysisException appActivityStartAfterEnd(UUID userAnonymizedID, String application, ZonedDateTime startTime,
			ZonedDateTime endTime)
	{
		return new AnalysisException("error.analysis.invalid.app.activity.data.end.before.start", userAnonymizedID, application,
				startTime, endTime);
	}

	public static AnalysisException appActivityStartsInFuture(UUID userAnonymizedID, String application, ZonedDateTime startTime)
	{
		return new AnalysisException("error.analysis.invalid.app.activity.data.starts.in.future", userAnonymizedID, application,
				startTime);
	}

	public static AnalysisException appActivityEndsInFuture(UUID userAnonymizedID, String application, ZonedDateTime endTime)
	{
		return new AnalysisException("error.analysis.invalid.app.activity.data.ends.in.future", userAnonymizedID, application, endTime);
	}
}
