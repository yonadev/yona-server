/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("confirmationCode")
public class ConfirmationCodeDTO
{
	private String code;

	public String getCode()
	{
		return code;
	}

	@JsonCreator
	public ConfirmationCodeDTO(@JsonProperty(value = "code", required = true) String code)
	{
		this.code = code;
	}
}
