/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class UserAnonymizedSynchronizer
{
	private enum LockStatus
	{
		FREE, LOCKED, LOCKED_BY_ME
	}

	private final Map<UUID, Thread> lockedUsers = new HashMap<>();

	public Lock lock(UUID userAnonymizedID)
	{
		try
		{
			synchronized (lockedUsers)
			{
				LockStatus lockStatus;
				while ((lockStatus = getLockStatus(userAnonymizedID)) == LockStatus.LOCKED)
				{
					lockedUsers.wait();
				}
				if (lockStatus == LockStatus.FREE)
				{
					storeLock(userAnonymizedID);
				}
				return new Lock(userAnonymizedID, lockStatus == LockStatus.FREE);
			}
		}
		catch (InterruptedException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private LockStatus getLockStatus(UUID userAnonymizedID)
	{
		Thread thread = lockedUsers.get(userAnonymizedID);
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

	private void storeLock(UUID userAnonymizedID)
	{
		if (lockedUsers.put(userAnonymizedID, Thread.currentThread()) != null)
		{
			throw new IllegalStateException("Map already contains a locking thread for user anonymized ID " + userAnonymizedID);
		}
	}

	private void unlock(UUID userAnonymizedID)
	{
		synchronized (lockedUsers)
		{
			lockedUsers.remove(userAnonymizedID);
			lockedUsers.notifyAll();
		}
	}

	public class Lock implements AutoCloseable
	{
		private final UUID userAnonymizedID;
		private final boolean mustUnlock;

		private Lock(UUID userAnonymizedID, boolean mustUnlock)
		{
			this.userAnonymizedID = userAnonymizedID;
			this.mustUnlock = mustUnlock;
		}

		@Override
		public void close()
		{
			if (mustUnlock)
			{
				unlock(userAnonymizedID);
			}
		}
	}
}
