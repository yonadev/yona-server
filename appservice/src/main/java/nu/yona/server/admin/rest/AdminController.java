/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.admin.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import nu.yona.server.DOSProtectionService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.UserServiceException;

@Controller
@RequestMapping(value = "/admin", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AdminController extends ControllerBase
{
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private DOSProtectionService dosProtectionService;

	@Autowired
	private YonaProperties yonaProperties;

	@PostMapping(value = "/requestUserOverwrite/")
	public ResponseEntity<Void> requestOverwriteUserConfirmationCode(@RequestParam String mobileNumber,
			HttpServletRequest request)
	{
		try
		{
			// Use DOS protection to prevent spamming numbers with confirmation code text messages
			// Do not include the mobile number in the URI. DOS protection should be number-independent
			URI uri = getRequestOverwriteUserConfirmationCodeLinkBuilder("NotSpecified").toUri();
			dosProtectionService.executeAttempt(uri, request,
					yonaProperties.getSecurity().getMaxRequestOverwriteUserConfirmationCodeAttemptsPerTimeWindow(),
					() -> userService.requestOverwriteUserConfirmationCode(mobileNumber));
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists
			logger.error("Caught UserServiceException. Ignoring it", e);
		}
		return createNoContentResponse();
	}

	private static WebMvcLinkBuilder getRequestOverwriteUserConfirmationCodeLinkBuilder(String mobileNumber)
	{
		AdminController methodOn = methodOn(AdminController.class);
		return linkTo(methodOn.requestOverwriteUserConfirmationCode(mobileNumber, null));
	}
}
