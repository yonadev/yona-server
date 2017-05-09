/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("user")
public class OverwriteUserDto
{
	/*
	 * Only intended for test purposes.
	 */
	private String confirmationCode;

	public OverwriteUserDto()
	{
		// Nothing to do here
	}

	/*
	 * Only intended for test purposes.
	 */
	public void setConfirmationCode(String confirmationCode)
	{
		this.confirmationCode = confirmationCode;
	}

	/*
	 * Only intended for test purposes.
	 */
	@JsonInclude(Include.NON_EMPTY)
	public String getConfirmationCode()
	{
		return confirmationCode;
	}

	public static OverwriteUserDto createInstance()
	{
		return new OverwriteUserDto();
	}
}
