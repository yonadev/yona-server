/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("postPutActivityCommentMessage")
public class PostPutActivityCommentMessageDto
{
	private final String message;

	@JsonCreator
	public PostPutActivityCommentMessageDto(@JsonProperty(value = "message", required = true) String message)
	{
		this.message = message;
	}

	public String getMessage()
	{
		return message;
	}
}
