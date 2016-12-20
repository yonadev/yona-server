/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import nu.yona.server.rest.ErrorResponseDto;

public class ConfirmationFailedResponseDto extends ErrorResponseDto
{
	private final int remainingAttempts;

	public ConfirmationFailedResponseDto(String code, String message, int remainingAttempts)
	{
		super(code, message);
		this.remainingAttempts = remainingAttempts;
	}

	public int getRemainingAttempts()
	{
		return remainingAttempts;
	}

}
