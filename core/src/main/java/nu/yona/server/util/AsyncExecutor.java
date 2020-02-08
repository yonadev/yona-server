/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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

	@Async
	public void execAsync(ThreadData threadData, Runnable action, Consumer<Optional<Throwable>> completionHandler)
	{
		try (Handler handler = Handler.initialize(headersHolder, threadData))
		{
			try
			{
				action.run();
				completionHandler.accept(Optional.empty());
			}
			catch (Throwable e)
			{
				completionHandler.accept(Optional.of(e));
				throw e;
			}
		}
	}

	private static class Handler implements AutoCloseable
	{
		private final ThreadData threadData;
		private final PassThroughHeadersHolder headersHolder;

		private Handler(PassThroughHeadersHolder headersHolder, ThreadData threadData)
		{
			this.headersHolder = headersHolder;
			this.threadData = threadData;
		}

		static Handler initialize(PassThroughHeadersHolder headersHolder, ThreadData threadData)
		{
			threadData.contextMap.ifPresent(MDC::setContextMap);
			headersHolder.importFrom(threadData.headers);
			return new Handler(headersHolder, threadData);
		}

		@Override
		public void close()
		{
			threadData.contextMap.ifPresent(m -> MDC.setContextMap(Collections.emptyMap()));
			headersHolder.removeAll(threadData.headers.keySet());
		}
	}
}