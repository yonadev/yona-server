/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.PreUpdate;

import org.springframework.stereotype.Component;

@Component
public class UserLockChecker
{
	private static EntityManager entityManager;

	@PersistenceContext
	public void setEntityManager(EntityManager entityManager)
	{
		UserLockChecker.entityManager = entityManager;
	}

	@PreUpdate
	void onPreUpdate(User user)
	{
		LockModeType lockMode = entityManager.getLockMode(user);
		if (lockMode != LockModeType.PESSIMISTIC_WRITE)
		{
			throw new IllegalStateException("Invalid lock mode: " + lockMode);
		}
	}
}
