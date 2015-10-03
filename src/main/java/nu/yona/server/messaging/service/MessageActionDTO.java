/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class MessageActionDTO {

	private Map<String, String> properties;

	@JsonCreator
	public MessageActionDTO(
			@JsonProperty("properties") @JsonDeserialize(as = HashMap.class, contentAs = String.class) Map<String, String> properties) {
		this.properties = new HashMap<>(properties);
	}

	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	public String getProperty(String name) {
		return properties.get(name);
	}
}
