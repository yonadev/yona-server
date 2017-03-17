/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import nu.yona.server.DOSProtectionService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/admin", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AdminController
{
	@Autowired
	private DOSProtectionService dosProtectionService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/requestUserOverwrite/", method = RequestMethod.POST)
	public ResponseEntity<String> setOverwriteUserConfirmationCode(@RequestParam String mobileNumber, HttpServletRequest request)
	{
		// use DOS protection to prevent enumeration of all occupied mobile numbers
		return dosProtectionService.executeAttempt(getSetOverwriteUserConfirmationCodeLinkBuilder().toUri(), request,
				yonaProperties.getSecurity().getMaxSetOverwriteUserConfirmationCodeAttemptsPerTimeWindow(), () -> {
					userService.setOverwriteUserConfirmationCode(mobileNumber);
					return ResponseEntity.status(HttpStatus.OK).body(null);
				});
	}

	private ControllerLinkBuilder getSetOverwriteUserConfirmationCodeLinkBuilder()
	{
		AdminController methodOn = methodOn(AdminController.class);
		return linkTo(methodOn.setOverwriteUserConfirmationCode("", null));
	}
}
