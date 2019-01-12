/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.UserServiceException;

@Controller
@RequestMapping(value = "/admin", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AdminController
{
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	@Autowired
	private UserService userService;

	@PostMapping(value = "/requestUserOverwrite/")
	@ResponseStatus(HttpStatus.OK)
	public void setOverwriteUserConfirmationCode(@RequestParam String mobileNumber)
	{
		try
		{
			userService.setOverwriteUserConfirmationCode(mobileNumber);
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists
			logger.error("Caught UserServiceException. Ignoring it", e);
		}
	}
}
