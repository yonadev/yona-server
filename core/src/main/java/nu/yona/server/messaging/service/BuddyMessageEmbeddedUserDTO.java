/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.subscriptions.service.UserDTO;

public abstract class BuddyMessageEmbeddedUserDTO extends BuddyMessageDTO
{
	private Map<String, Object> embeddedResources = Collections.emptyMap();

	protected BuddyMessageEmbeddedUserDTO(UUID id, ZonedDateTime creationTime, boolean isRead, Optional<UserDTO> senderUser,
			String senderNickname, String message)
	{
		super(id, SenderInfo.createInstanceBuddyDetached(senderUser, senderNickname), creationTime, isRead, senderUser, message);
	}

	@JsonProperty("_embedded")
	@JsonInclude(Include.NON_EMPTY)
	public Map<String, Object> getEmbeddedResources()
	{
		return embeddedResources;
	}

	public void setEmbeddedUser(String rel, Object userResource)
	{
		embeddedResources = Collections.singletonMap(rel, userResource);
	}
}
