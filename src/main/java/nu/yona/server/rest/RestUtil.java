/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;

public class RestUtil {

	private RestUtil() {
		// No instances
	}

	public static Link selfLinkWithTrailingSlash(ControllerLinkBuilder linkBuilder) {
		return linkWithTrailingSlash(linkBuilder, Link.REL_SELF);
	}

	public static Link linkWithTrailingSlash(ControllerLinkBuilder linkBuilder, String rel) {
		return new Link(linkBuilder.toUriComponentsBuilder().build().toUriString() + "/", rel);
	}
}
