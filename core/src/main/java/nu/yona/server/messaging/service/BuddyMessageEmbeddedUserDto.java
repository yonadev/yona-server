/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class BuddyMessageEmbeddedUserDto extends BuddyMessageDto
{
	private Map<String, Object> embeddedResources = Collections.emptyMap();

	protected BuddyMessageEmbeddedUserDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message)
	{
		super(id, creationTime, isRead, senderInfo, message);
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
