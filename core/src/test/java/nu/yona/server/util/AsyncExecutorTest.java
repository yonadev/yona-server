/*******************************************************************************
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.rest.PassThroughHeadersHolder;

@Configuration
@EnableAsync
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.rest", "nu.yona.server.util" }, includeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.rest.PassThroughHeadersHolder", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.util.AsyncExecutor", type = FilterType.REGEX) })
class AsyncExecutorTestConfiguration extends UserRepositoriesConfiguration
{
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { AsyncExecutorTestConfiguration.class })
class AsyncExecutorTest
{
	@Autowired
	private AsyncExecutor service;

	@Autowired
	private PassThroughHeadersHolder headersHolder;

	@Test
	void getThreadData_successful_dataIsPresent() throws InterruptedException
	{
		DataCarrier expectedData = initializeThread();
		CountDownLatch doneSignal = new CountDownLatch(1);
		DataCarrier actionHandlerData = new DataCarrier();
		DataCarrier completionHandlerData = new DataCarrier();

		service.execAsync(service.getThreadData(), () -> actionHandler(actionHandlerData, Optional.empty()),
				(t) -> completionHandler(doneSignal, completionHandlerData, t));
		doneSignal.await();

		assertActionHandlerData(actionHandlerData, expectedData);
		assertCompletionHandlerData(completionHandlerData, expectedData);
	}

	@Test
	void getThreadData_failed_dataIsPresentThrowableIsAvailable() throws InterruptedException
	{
		DataCarrier expectedData = initializeThread();
		expectedData.exception = Optional.of(new NullPointerException("This fails badly"));
		CountDownLatch doneSignal = new CountDownLatch(1);
		DataCarrier actionHandlerData = new DataCarrier();
		DataCarrier completionHandlerData = new DataCarrier();

		service.execAsync(service.getThreadData(), () -> actionHandler(actionHandlerData, expectedData.exception),
				(t) -> completionHandler(doneSignal, completionHandlerData, t));
		doneSignal.await();

		assertActionHandlerData(actionHandlerData, expectedData);
		assertCompletionHandlerData(completionHandlerData, expectedData);
	}

	private DataCarrier initializeThread()
	{
		DataCarrier dataTransfer = new DataCarrier();
		dataTransfer.mdc = Map.of("MDC key1", UUID.randomUUID().toString(), "MDC key2", UUID.randomUUID().toString());
		MDC.clear();
		dataTransfer.mdc.entrySet().stream().forEach(e -> MDC.put(e.getKey(), e.getValue()));

		dataTransfer.headers = Map.of("header1", UUID.randomUUID().toString(), "header2", UUID.randomUUID().toString());
		headersHolder.importFrom(dataTransfer.headers);

		return dataTransfer;
	}

	private void actionHandler(DataCarrier returnDataHolder, Optional<Throwable> exception)
	{
		returnDataHolder.mdc = MDC.getCopyOfContextMap();
		returnDataHolder.headers = headersHolder.export();
		returnDataHolder.threadName = Thread.currentThread().getName();
		exception.ifPresent(e -> {
			throw (RuntimeException) e;
		});
	}

	private void completionHandler(CountDownLatch doneSignal, DataCarrier returnDataHolder, Optional<Throwable> exception)
	{
		returnDataHolder.mdc = MDC.getCopyOfContextMap();
		returnDataHolder.headers = headersHolder.export();
		returnDataHolder.threadName = Thread.currentThread().getName();
		returnDataHolder.exception = exception;
		doneSignal.countDown();
	}

	private void assertActionHandlerData(DataCarrier returnedData, DataCarrier expectedData)
	{
		assertThat(returnedData.mdc, is(expectedData.mdc));
		assertThat(returnedData.headers, is(expectedData.headers));
		assertNotEquals(Thread.currentThread().getName(), returnedData.threadName);
	}

	private void assertCompletionHandlerData(DataCarrier returnedData, DataCarrier expectedData)
	{
		assertActionHandlerData(returnedData, expectedData);
		assertEquals(expectedData.exception, returnedData.exception);
	}

	private static class DataCarrier
	{
		Map<String, String> headers;
		Map<String, String> mdc;
		Optional<Throwable> exception = Optional.empty();
		String threadName;
	}
}