/*
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.util;

import java.io.Serializable;

import org.hibernate.LockMode;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nu.yona.server.exceptions.YonaException;

@Service
public class HibernateHelperService
{
	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * Clears the Hibernate session if we didn't yet lock the user entity. This way, we are sure that we are not using stale
	 * entities that were loaded before the previous transaction released its lock on the User entity.
	 *
	 * @param id the ID if the user for which we need to check the lock
	 */
	public void clearSessionIfNotLockedYet(Class<?> clazz, Serializable id)
	{
		Session session = getSession();
		Object entity = session.get(clazz, id);
		if (entity != null && isLockedForUpdate(session, entity))
		{
			// Already acquired an update lock
			return;
		}
		Require.that(!session.isDirty(),
				() -> YonaException.illegalState("withLockOnUser must be called before the first update"));
		session.clear();
	}

	private Session getSession()
	{
		return entityManager.unwrap(Session.class);
	}

	private boolean isLockedForUpdate(Session session, Object entity)
	{
		return session.getCurrentLockMode(entity) == LockMode.PESSIMISTIC_WRITE;
	}

	public boolean isLockedForUpdate(Object entity)
	{
		return isLockedForUpdate(getSession(), entity);
	}
}
