/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

@JsonRootName("buddy")
public class BuddyDTO
{
	public static final String USER_REL_NAME = "user";
	private final UUID id;
	private UserDTO user;
	private String message;
	private String password;
	private String nickName;
	private UUID loginID;
	private Status sendingStatus;
	private Status receivingStatus;

	/*
	 * Only intended for test purposes.
	 */
	private String userCreatedInviteURL;

	public BuddyDTO(UUID id, UserDTO user, String message, String password, String nickName, UUID loginID, Status sendingStatus,
			Status receivingStatus)
	{
		this.id = id;
		this.user = user;
		this.message = message;
		this.password = password;
		this.nickName = nickName;
		this.loginID = loginID;
		this.sendingStatus = sendingStatus;
		this.receivingStatus = receivingStatus;
	}

	public BuddyDTO(UUID id, UserDTO user, String nickName, UUID loginID, Status sendingStatus, Status receivingStatus)
	{
		this(id, user, null, null, nickName, loginID, sendingStatus, receivingStatus);
	}

	@JsonCreator
	public BuddyDTO(@JsonProperty("_embedded") Map<String, UserDTO> userInMap, @JsonProperty("nickName") String nickName,
			@JsonProperty("message") String message, @JsonProperty("password") String password,
			@JsonProperty("sendingStatus") Status sendingStatus, @JsonProperty("receivingStatus") Status receivingStatus)
	{
		this(null, userInMap.get(USER_REL_NAME), message, password, nickName, null, sendingStatus, receivingStatus);
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonIgnore
	public String getMessage()
	{
		return message;
	}

	@JsonIgnore
	public UserDTO getUser()
	{
		return user;
	}

	Buddy createBuddyEntity()
	{
		return Buddy.createInstance(user.getID(), user.getPrivateData().getNickName(), getSendingStatus(), getReceivingStatus());
	}

	public static BuddyDTO createInstance(Buddy buddyEntity)
	{
		return new BuddyDTO(buddyEntity.getID(), UserDTO.createInstance(buddyEntity.getUser()), buddyEntity.getNickName(),
				getBuddyLoginID(buddyEntity), buddyEntity.getSendingStatus(), buddyEntity.getReceivingStatus());
	}

	private static UUID getBuddyLoginID(Buddy buddyEntity)
	{
		return (buddyEntity.getReceivingStatus() == Status.ACCEPTED) || (buddyEntity.getSendingStatus() == Status.ACCEPTED)
				? buddyEntity.getLoginID() : null;
	}

	@JsonInclude(Include.NON_EMPTY)
	public String getPassword()
	{
		return password;
	}

	public String getNickName()
	{
		return nickName;
	}

	public Status getSendingStatus()
	{
		return sendingStatus;
	}

	public Status getReceivingStatus()
	{
		return receivingStatus;
	}

	@JsonIgnore
	public UUID getLoginID()
	{
		return loginID;
	}

	/*
	 * Only intended for test purposes.
	 */
	public void setUserCreatedInviteURL(String userCreatedInviteURL)
	{
		this.userCreatedInviteURL = userCreatedInviteURL;
	}

	/*
	 * Only intended for test purposes.
	 */
	@JsonInclude(Include.NON_EMPTY)
	public String getUserCreatedInviteURL()
	{
		return userCreatedInviteURL;
	}
}
