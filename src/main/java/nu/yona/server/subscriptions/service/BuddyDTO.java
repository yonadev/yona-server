/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;

public class BuddyDTO {
	public static final String USER_REL_NAME = "user";
	private final UUID id;
	private UserDTO user;
	private String message;
	private String password;

	public BuddyDTO(UUID id, UserDTO userResource, String message, String password) {
		this.id = id;
		this.user = userResource;
		this.message = message;
		this.password = password;
	}

	public BuddyDTO(UUID id, UserDTO userResource) {
		this(id, userResource, null, null);
	}

	@JsonCreator
	public BuddyDTO(@JsonProperty("_embedded") Map<String, UserDTO> userInMap, @JsonProperty("message") String message,
			@JsonProperty("password") String password) {
		this(null, userInMap.get(USER_REL_NAME), message, password);
	}

	@JsonIgnore
	public UUID getID() {
		return id;
	}

	@JsonIgnore
	public String getMessage() {
		return message;
	}

	@JsonIgnore
	public UserDTO getUser() {
		return user;
	}

	Buddy createBuddyEntity(User buddyUserEntity) {
		return Buddy.createInstance(buddyUserEntity, user.getNickName());
	}

	public static BuddyDTO createInstance(Buddy buddyEntity) {
		return new BuddyDTO(buddyEntity.getID(), UserDTO.createInstance(buddyEntity.getUser()));
	}

	@JsonInclude(Include.NON_EMPTY)
	public String getPassword() {
		return password;
	}
}
