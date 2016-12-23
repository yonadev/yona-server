/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * This exception is thrown for various issues that can occur during analysis of app or network activity.
 */
public class AnalysisException extends YonaException
{
	private static final long serialVersionUID = -366842642655183778L;

	private AnalysisException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private AnalysisException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static AnalysisException appActivityStartAfterEnd(UUID userAnonymizedId, String application, ZonedDateTime startTime,
			ZonedDateTime endTime)
	{
		return new AnalysisException("error.analysis.invalid.app.activity.data.end.before.start", userAnonymizedId, application,
				startTime, endTime);
	}

	public static AnalysisException appActivityStartsInFuture(UUID userAnonymizedId, String application, ZonedDateTime startTime)
	{
		return new AnalysisException("error.analysis.invalid.app.activity.data.starts.in.future", userAnonymizedId, application,
				startTime);
	}

	public static AnalysisException appActivityEndsInFuture(UUID userAnonymizedId, String application, ZonedDateTime endTime)
	{
		return new AnalysisException("error.analysis.invalid.app.activity.data.ends.in.future", userAnonymizedId, application,
				endTime);
	}

	public static AnalysisException urlTooLong(int length, int maxSupportedUrlLength)
	{
		return new AnalysisException("error.analysis.invalid.network.activity.url.too.long", length, maxSupportedUrlLength);
	}
}
