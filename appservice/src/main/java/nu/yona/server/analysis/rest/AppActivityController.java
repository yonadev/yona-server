/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.analysis.service.AnalysisEngineProxyService;
import nu.yona.server.analysis.service.AppActivityDto;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Controller to push mobile app activity from the Yona app. The Yona app registers this activity locally and will send them to
 * the application service once there is a network connection.
 */
@Controller
@RequestMapping(value = "/users/{userId}/appActivity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AppActivityController
{
	@Autowired
	private UserService userService;

	@Autowired
	private AnalysisEngineProxyService analysisEngineProxyService;

	/*
	 * Adds app activity registered by the Yona app. This request is delegated to the analysis engine service.
	 * @param password User password, validated before adding the activity.
	 * @param appActivities Because it may be that multiple app activities may have taken place during the time the network is
	 * down, accept an array of activities.
	 */
	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> addAppActivity(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody AppActivityDto appActivities)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			UUID userAnonymizedId = userService.getPrivateUser(userId).getPrivateData().getUserAnonymizedId();
			analysisEngineProxyService.analyzeAppActivity(userAnonymizedId, appActivities);
			return new ResponseEntity<>(HttpStatus.OK);
		}
	}

	public static Link getAppActivityLink(UUID userId)
	{
		try
		{
			ControllerLinkBuilder linkBuilder = linkTo(
					methodOn(AppActivityController.class).addAppActivity(Optional.empty(), userId, null));
			return linkBuilder.withRel("appActivity");
		}
		catch (SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
