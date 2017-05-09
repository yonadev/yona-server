/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.util.HashMap;
import java.util.Map;

import nu.yona.server.exceptions.YonaException;

public class LockPool<T>
{
	private enum LockStatus
	{
		FREE, LOCKED, LOCKED_BY_ME
	}

	private final Map<T, Thread> pool = new HashMap<>();

	public Lock lock(T id)
	{
		try
		{
			synchronized (pool)
			{
				LockStatus lockStatus;
				while ((lockStatus = getLockStatus(id)) == LockStatus.LOCKED)
				{
					pool.wait();
				}
				if (lockStatus == LockStatus.FREE)
				{
					storeLock(id);
				}
				return new Lock(id, lockStatus == LockStatus.FREE);
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw YonaException.unexpected(e);
		}
	}

	private LockStatus getLockStatus(T id)
	{
		Thread thread = pool.get(id);
		if (thread == null)
		{
			return LockStatus.FREE;
		}
		if (thread == Thread.currentThread())
		{
			return LockStatus.LOCKED_BY_ME;
		}
		return LockStatus.LOCKED;
	}

	private void storeLock(T id)
	{
		if (pool.put(id, Thread.currentThread()) != null)
		{
			throw new IllegalStateException("Map already contains a locking thread for ID " + id);
		}
	}

	private void unlock(T id)
	{
		synchronized (pool)
		{
			pool.remove(id);
			pool.notifyAll();
		}
	}

	public class Lock implements AutoCloseable
	{
		private final T id;
		private final boolean mustUnlock;

		private Lock(T id, boolean mustUnlock)
		{
			this.id = id;
			this.mustUnlock = mustUnlock;
		}

		@Override
		public void close()
		{
			if (mustUnlock)
			{
				unlock(id);
			}
		}
	}
}
