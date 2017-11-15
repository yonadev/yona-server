/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
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
import nu.yona.server.analysis.service.AppActivityDto.Activity;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Controller to push mobile app activity from the Yona app. The Yona app registers this activity locally and will send them to
 * the application service once there is a network connection.
 */
@Controller
@RequestMapping(value = "/users/{userId}/appActivity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AppActivityController extends ControllerBase
{
	private static final Logger logger = LoggerFactory.getLogger(AppActivityController.class);

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private UserService userService;

	@Autowired
	private AnalysisEngineProxyService analysisEngineProxyService;

	private enum MessageType
	{
		ERROR, WARNING
	}

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
		if (appActivities.getActivities().length > yonaProperties.getAnalysisService().getAppActivityCountIgnoreThreshold())
		{
			logLongAppActivityBatch(MessageType.ERROR, userId, appActivities);
			return createOkResponse();
		}
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			UUID userAnonymizedId = userService.getPrivateUser(userId).getPrivateData().getUserAnonymizedId();
			if (appActivities.getActivities().length > yonaProperties.getAnalysisService().getAppActivityCountLoggingThreshold())
			{
				logLongAppActivityBatch(MessageType.WARNING, userId, appActivities);
			}
			analysisEngineProxyService.analyzeAppActivity(userAnonymizedId, appActivities);
			return createOkResponse();
		}
	}

	private void logLongAppActivityBatch(MessageType messageType, UUID userId, AppActivityDto appActivities)
	{
		int numAppActivities = appActivities.getActivities().length;
		List<Activity> appActivityCollection = Arrays.asList(appActivities.getActivities());
		Comparator<? super Activity> comparator = (a, b) -> a.getStartTime().compareTo(b.getStartTime());
		ZonedDateTime minStartTime = Collections.min(appActivityCollection, comparator).getStartTime();
		ZonedDateTime maxStartTime = Collections.max(appActivityCollection, comparator).getStartTime();
		switch (messageType)
		{
			case ERROR:
				logger.error(
						"User with ID {} posts too many ({}) app activities, with start dates ranging from {} to {} (device time: {}). App activities ignored.",
						userId, numAppActivities, minStartTime, maxStartTime, appActivities.getDeviceDateTime());
				break;
			case WARNING:
				logger.warn(
						"User with ID {} posts many ({}) app activities, with start dates ranging from {} to {} (device time: {})",
						userId, numAppActivities, minStartTime, maxStartTime, appActivities.getDeviceDateTime());
				break;
			default:
				throw new IllegalStateException("Unsupported message type: " + messageType);
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
