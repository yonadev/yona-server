/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import nu.yona.server.entities.EntityWithUuid;

@MappedSuperclass
public abstract class EntityWithUuidAndTouchVersion extends EntityWithUuid
{
	@Column(nullable = true)
	private int touchVersion;

	protected EntityWithUuidAndTouchVersion(UUID id)
	{
		super(id);
	}

	public EntityWithUuidAndTouchVersion touch()
	{
		touchVersion++;
		return this;
	}

}