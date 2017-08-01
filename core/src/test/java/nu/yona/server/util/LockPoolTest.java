/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import nu.yona.server.exceptions.YonaException;

public class LockPoolTest
{
	private final AtomicInteger freeLockId = new AtomicInteger(0);

	@Test
	public void lock_accessSameLockIdConcurrently_isNotExecutedConcurrently()
	{
		final int numThreads = 10;
		final int iterations = 100;
		assertThat(testConcurrently(numThreads, iterations, this::attemptNonReentrantAccess), equalTo(false));
	}

	@Test
	public void lock_reentrantAccess_isNotBlocked()
	{
		final int numThreads = 10;
		final int iterations = 100;
		assertThat(testConcurrently(numThreads, iterations, this::attemptReentrantAccess), equalTo(false));
	}

	@Test
	public void lock_accessFreeLockIdsConcurrently_immediateAccess()
	{
		final int numThreads = 10;
		final int iterations = 5;
		assertThat(testConcurrently(numThreads, iterations, this::attempImmediateAccessFreeLockId), equalTo(false));
	}

	private boolean testConcurrently(int numThreads, int iterations, AccessAttempt accessAttempt)
	{
		LockPool<Integer> lockPool = new LockPool<>();
		ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
		CountDownLatch startSignal = new CountDownLatch(1);
		CountDownLatch doneSignal = new CountDownLatch(numThreads);
		AtomicBoolean concurrencyIndicator = new AtomicBoolean(false);
		AtomicBoolean failureIndicator = new AtomicBoolean(false);
		for (int i = 0; (i < numThreads); i++)
		{
			threadPool.execute(() -> tryAccess(iterations, startSignal, lockPool, concurrencyIndicator, failureIndicator,
					doneSignal, accessAttempt));
		}
		startSignal.countDown();
		awaitWithoutInterrupt(doneSignal);
		threadPool.shutdown();
		return failureIndicator.get();
	}

	private void tryAccess(int iterations, CountDownLatch startSignal, LockPool<Integer> lockPool,
			AtomicBoolean concurrencyIndicator, AtomicBoolean failureIndicator, CountDownLatch doneSignal,
			AccessAttempt accessAttempt)
	{
		awaitWithoutInterrupt(startSignal);
		for (int i = 0; (i < iterations); i++)
		{
			accessAttempt.attempt(lockPool, concurrencyIndicator, failureIndicator);
		}
		doneSignal.countDown();
	}

	private void attemptNonReentrantAccess(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator,
			AtomicBoolean failureIndicator)
	{
		int lockId = 0;
		try (LockPool<Integer>.Lock lock = lockPool.lock(lockId))
		{
			verifyNonConcurrentExecution(concurrencyIndicator, failureIndicator);
		}
	}

	private void attemptReentrantAccess(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator,
			AtomicBoolean failureIndicator)
	{
		int lockId = 0;
		try (LockPool<Integer>.Lock lock1 = lockPool.lock(lockId))
		{
			verifyNonConcurrentExecution(concurrencyIndicator, failureIndicator);
			try (LockPool<Integer>.Lock lock2 = lockPool.lock(lockId))
			{
				verifyNonConcurrentExecution(concurrencyIndicator, failureIndicator);
			}
			verifyNonConcurrentExecution(concurrencyIndicator, failureIndicator);
		}
	}

	private void attempImmediateAccessFreeLockId(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator,
			AtomicBoolean failureIndicator)
	{
		int lockId = freeLockId.incrementAndGet();
		int sleepDuration = 500;
		long startTime = System.currentTimeMillis();
		long accessTime;
		try (LockPool<Integer>.Lock lock1 = lockPool.lock(lockId))
		{
			try (LockPool<Integer>.Lock lock2 = lockPool.lock(lockId))
			{
				accessTime = System.currentTimeMillis();
				sleepWithoutInterrupt(sleepDuration);
			}
		}
		if (accessTime - startTime > sleepDuration / 2)
		{
			// Delay was more than half the sleep duration, so we apparently were held up
			// Comparing to the exact sleep duration is tricky, as the contract for Thread.sleep mentions accuracy of the system
			// timers.
			failureIndicator.set(true);
		}
	}

	private void verifyNonConcurrentExecution(AtomicBoolean concurrencyIndicator, AtomicBoolean failureIndicator)
	{
		if (concurrencyIndicator.compareAndSet(false, true))
		{
			sleepWithoutInterrupt(1);
			// No one else is here
			concurrencyIndicator.set(false);
		}
		else
		{
			// Someone else is here
			failureIndicator.set(true);
		}
	}

	private void awaitWithoutInterrupt(CountDownLatch startSignal)
	{
		try
		{
			startSignal.await();
		}
		catch (InterruptedException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void sleepWithoutInterrupt(long millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@FunctionalInterface
	static interface AccessAttempt
	{
		void attempt(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator, AtomicBoolean failureIndicator);
	}
}
