/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.util.Map;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.rest.PassThroughHeadersHolder;

@Service
public class AsyncExecutor
{
	@Autowired
	private PassThroughHeadersHolder headersHolder;

	public static class ThreadData
	{

		final Optional<Map<String, String>> contextMap;
		final Map<String, String> headers;

		public ThreadData(Map<String, String> contextMap, Map<String, String> headers)
		{
			this.contextMap = Optional.ofNullable(contextMap);
			this.headers = headers;
		}

	}

	public ThreadData getThreadData()
	{
		return new ThreadData(MDC.getCopyOfContextMap(), headersHolder.export());
	}

	public void initThreadAndDo(ThreadData threadData, Runnable action)
	{
		threadData.contextMap.ifPresent(MDC::setContextMap);
		headersHolder.importFrom(threadData.headers);
		action.run();
	}
}
