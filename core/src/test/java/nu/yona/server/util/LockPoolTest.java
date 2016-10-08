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
	private final AtomicInteger freeLockID = new AtomicInteger(0);

	@Test
	public void testLockingNonReentrant()
	{
		final int numThreads = 10;
		final int iterations = 10000;
		assertThat(testConcurrently(numThreads, iterations, this::attemptNonReentrantAccess), equalTo(0));
	}

	@Test
	public void testLockingReentrant()
	{
		final int numThreads = 10;
		final int iterations = 10000;
		assertThat(testConcurrently(numThreads, iterations, this::attemptReentrantAccess), equalTo(0));
	}

	@Test
	public void testConcurrencyDifferentKeys()
	{
		final int numThreads = 10;
		final int iterations = 5;
		assertThat(testConcurrently(numThreads, iterations, this::attempImmediateAccessRandomID), equalTo(0));
	}

	private int testConcurrently(int numThreads, int iterations, AccessAttempt accessAttempt)
	{
		LockPool<Integer> lockPool = new LockPool<>();
		ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
		CountDownLatch startSignal = new CountDownLatch(1);
		CountDownLatch doneSignal = new CountDownLatch(numThreads);
		AtomicBoolean concurrencyIndicator = new AtomicBoolean(false);
		AtomicInteger failureCounter = new AtomicInteger(0);
		for (int i = 0; (i < numThreads); i++)
		{
			threadPool.execute(() -> tryAccess(iterations, startSignal, lockPool, concurrencyIndicator, failureCounter,
					doneSignal, accessAttempt));
		}
		startSignal.countDown();
		awaitWithoutInterrupt(doneSignal);
		threadPool.shutdown();
		return failureCounter.get();
	}

	private void tryAccess(int iterations, CountDownLatch startSignal, LockPool<Integer> lockPool,
			AtomicBoolean concurrencyIndicator, AtomicInteger failureCounter, CountDownLatch doneSignal,
			AccessAttempt accessAtempt)
	{
		awaitWithoutInterrupt(startSignal);
		for (int i = 0; (i < iterations); i++)
		{
			accessAtempt.attempt(lockPool, concurrencyIndicator, failureCounter);
		}
		doneSignal.countDown();
	}

	private void attemptNonReentrantAccess(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator,
			AtomicInteger failureCounter)
	{
		int lockID = 0;
		try (LockPool<Integer>.Lock lock = lockPool.lock(lockID))
		{
			verifyNonConcurrentExecution(concurrencyIndicator, failureCounter);
		}
	}

	private void attemptReentrantAccess(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator,
			AtomicInteger failureCounter)
	{
		int lockID = 0;
		try (LockPool<Integer>.Lock lock1 = lockPool.lock(0))
		{
			verifyNonConcurrentExecution(concurrencyIndicator, failureCounter);
			try (LockPool<Integer>.Lock lock2 = lockPool.lock(lockID))
			{
				verifyNonConcurrentExecution(concurrencyIndicator, failureCounter);
			}
			verifyNonConcurrentExecution(concurrencyIndicator, failureCounter);
		}
	}

	private void attempImmediateAccessRandomID(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator,
			AtomicInteger failureCounter)
	{
		int lockID = freeLockID.incrementAndGet();
		int sleepDuration = 500;
		long startTime = System.currentTimeMillis();
		long accessTime;
		try (LockPool<Integer>.Lock lock1 = lockPool.lock(lockID))
		{
			try (LockPool<Integer>.Lock lock2 = lockPool.lock(lockID))
			{
				accessTime = System.currentTimeMillis();
				sleepWithoutInterrupt(sleepDuration);
			}
		}
		if (accessTime - startTime > sleepDuration / 2)
		{
			// Delay was more than half the sleep duration, so we apparently were held up
			failureCounter.incrementAndGet();
		}
	}

	private void verifyNonConcurrentExecution(AtomicBoolean concurrencyIndicator, AtomicInteger failureCounter)
	{
		if (concurrencyIndicator.compareAndSet(false, true))
		{
			// No one else is here
			concurrencyIndicator.set(false);
		}
		else
		{
			// Someone else is here
			failureCounter.incrementAndGet();
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
		void attempt(LockPool<Integer> lockPool, AtomicBoolean concurrencyIndicator, AtomicInteger testCounter);
	}
}
