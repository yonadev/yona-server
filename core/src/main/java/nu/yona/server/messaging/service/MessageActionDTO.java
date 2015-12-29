/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonRootName("messageAction")
public class MessageActionDTO
{

	private Map<String, String> properties;

	private Set<MessageDTO> affectedMessages;

	@JsonCreator
	public MessageActionDTO(
			@JsonProperty("properties") @JsonDeserialize(as = HashMap.class, contentAs = String.class) Map<String, String> properties)
	{
		this(properties, new HashSet<MessageDTO>());
	}

	private MessageActionDTO(Map<String, String> properties, Set<MessageDTO> affectedMessages)
	{
		this.properties = new HashMap<>(properties);
		this.affectedMessages = new HashSet<>(affectedMessages);
	}

	public static MessageActionDTO createInstanceActionDone(MessageDTO... affectedMessages)
	{
		return new MessageActionDTO(Collections.singletonMap("status", "done"),
				Arrays.stream(affectedMessages).collect(Collectors.toSet()));
	}

	public Map<String, String> getProperties()
	{
		return Collections.unmodifiableMap(properties);
	}

	@JsonIgnore
	public Set<MessageDTO> getAffectedMessages()
	{
		return Collections.unmodifiableSet(affectedMessages);
	}

	public String getProperty(String name)
	{
		return properties.get(name);
	}
}
