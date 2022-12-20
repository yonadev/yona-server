/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.util.StringUtil;

@JsonRootName("systemMessageSendRequest")
public class SystemMessageSendRequestDto
{
	private final String messageText;

	@JsonCreator
	public SystemMessageSendRequestDto(@JsonProperty("messageText") String messageText)
	{
		StringUtil.assertPlainTextCharactersWithUrls(messageText, "messageText");
		this.messageText = messageText;
	}

	public String getMessageText()
	{
		return messageText;
	}
}
