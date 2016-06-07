/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/admin", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AdminController
{

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/requestUserOverwrite/", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	public void setOverwriteUserConfirmationCode(@RequestParam String mobileNumber)
	{
		userService.setOverwriteUserConfirmationCode(mobileNumber);
	}
}
