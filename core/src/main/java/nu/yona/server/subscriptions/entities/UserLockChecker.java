/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Objects;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.PreUpdate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class UserLockChecker
{
	static class IdentityKey
	{
		private Object object;

		public IdentityKey(Object object)
		{
			this.object = Objects.requireNonNull(object);
		}

		@Override
		public int hashCode()
		{
			return System.identityHashCode(object);
		}

		@Override
		public boolean equals(Object that)
		{
			if (this == that)
			{
				return true;
			}
			if (!(that instanceof IdentityKey))
			{
				return false;
			}
			return this.object == ((IdentityKey) that).object;
		}
	}

	private static EntityManager entityManager;

	@PersistenceContext
	public void setEntityManager(EntityManager entityManager)
	{
		UserLockChecker.entityManager = entityManager;
	}

	@PreUpdate
	void onPreUpdate(User user)
	{
		if (isFlushedAlready(user))
		{
			return;
		}
		LockModeType lockMode = entityManager.getLockMode(user);
		if (lockMode != LockModeType.PESSIMISTIC_WRITE)
		{
			throw new IllegalStateException("Invalid lock mode: " + lockMode);
		}
		registerAsFlushedAlready(user);
	}

	private boolean isFlushedAlready(User user)
	{
		return TransactionSynchronizationManager.hasResource(buildResourceKey(user));
	}

	private void registerAsFlushedAlready(User user)
	{
		TransactionSynchronizationManager.bindResource(buildResourceKey(user), Boolean.TRUE);
	}

	private IdentityKey buildResourceKey(User user)
	{
		return new IdentityKey(user);
	}
}