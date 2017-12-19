/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import nu.yona.server.rest.ControllerBase;

/*
 * Controller to push mobile app activity from the Yona app. The Yona app registers this activity locally and will send them to
 * the application service once there is a network connection.
 */
@Controller
@RequestMapping(value = "/users/{userId}/appActivity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AppActivityController extends ControllerBase
{
}
