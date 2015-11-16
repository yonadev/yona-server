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

public class BuddyDTO {
	public static final String USER_REL_NAME = "user";
	private final UUID id;
	private UserDTO user;
	private String message;
	private String password;
	private String nickName;
	private UUID loginID;

	public BuddyDTO(UUID id, UserDTO user, String message, String password, String nickName, UUID loginID) {
		this.id = id;
		this.user = user;
		this.message = message;
		this.password = password;
		this.nickName = nickName;
		this.loginID = loginID;
	}

	public BuddyDTO(UUID id, UserDTO user, String nickName, UUID loginID) {
		this(id, user, null, null, nickName, loginID);
	}

	@JsonCreator
	public BuddyDTO(@JsonProperty("_embedded") Map<String, UserDTO> userInMap, @JsonProperty("message") String message,
			@JsonProperty("password") String password) {
		this(null, userInMap.get(USER_REL_NAME), message, password, null, null);
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

	Buddy createBuddyEntity() {
		return Buddy.createInstance(user.getID(), user.getPrivateData().getNickName());
	}

	public static BuddyDTO createInstance(Buddy buddyEntity) {
		return new BuddyDTO(buddyEntity.getID(), UserDTO.createInstance(buddyEntity.getUser()),
				buddyEntity.getNickName(), buddyEntity.getAccessorID());
	}

	@JsonInclude(Include.NON_EMPTY)
	public String getPassword() {
		return password;
	}

	@JsonIgnore
	public UUID getLoginID() {
		return loginID;
	}

	@JsonIgnore
	public String getNickName() {
		return nickName;
	}
}
