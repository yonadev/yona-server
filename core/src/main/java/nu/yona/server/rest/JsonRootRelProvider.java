/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import org.atteo.evo.inflector.English;
import org.springframework.hateoas.RelProvider;
import org.springframework.hateoas.core.DefaultRelProvider;

import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * When an embedded resource is provided in a response using the
 * {@code org.springframework.hateoas.Resources} model, this provider can be
 * configured at runtime to make any embedded values root json name be set based
 * on the classes annotated {@code JsonRootName ( " name " )}. By default Spring
 * hateoas renders the embedded root field based on the class name with first
 * character in lowercase.
 */
public class JsonRootRelProvider implements RelProvider {

	private DefaultRelProvider defaultRelProvider = new DefaultRelProvider();

	@Override
	public String getItemResourceRelFor(Class<?> type) {
		JsonRootName[] jsonRootNameAnnotations = type.getAnnotationsByType(JsonRootName.class);
		JsonRootName rootName = (jsonRootNameAnnotations.length == 0) ? null : jsonRootNameAnnotations[0];
		return (rootName == null) ? defaultRelProvider.getItemResourceRelFor(type) : rootName.value();
	}

	@Override
	public String getCollectionResourceRelFor(Class<?> type) {
		return English.plural(getItemResourceRelFor(type));
	}

	@Override
	public boolean supports(Class<?> delimiter) {
		return defaultRelProvider.supports(delimiter);
	}
}
