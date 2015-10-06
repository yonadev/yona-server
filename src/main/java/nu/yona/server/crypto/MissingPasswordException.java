/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.rest.Constants;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Header " + Constants.PASSWORD_HEADER + " not provided")
public class MissingPasswordException extends YonaException {

	private static final long serialVersionUID = 1989876591001478378L;

	public MissingPasswordException() {
		super("Password not provided");
	}
}
