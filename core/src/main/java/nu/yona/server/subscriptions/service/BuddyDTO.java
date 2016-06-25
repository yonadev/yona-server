/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Translator;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

@JsonRootName("buddy")
public class BuddyDTO
{
	public static final String USER_REL_NAME = "user";
	public static final String GOALS_REL_NAME = "goals";

	private final UUID id;
	private final UserDTO user;
	private final String message;
	private final String nickname;
	private final UUID userAnonymizedID;
	private final Status sendingStatus;
	private final Status receivingStatus;
	private Set<GoalDTO> goals = Collections.emptySet();

	public BuddyDTO(UUID id, UserDTO user, String message, String nickname, UUID userAnonymizedID, Status sendingStatus,
			Status receivingStatus)
	{
		this.id = id;
		this.user = user;
		this.message = message;
		this.nickname = nickname;
		this.userAnonymizedID = userAnonymizedID;
		this.sendingStatus = sendingStatus;
		this.receivingStatus = receivingStatus;
	}

	public BuddyDTO(UUID id, UserDTO user, String nickname, UUID userAnonymizedID, Status sendingStatus, Status receivingStatus)
	{
		this(id, user, null, nickname, userAnonymizedID, sendingStatus, receivingStatus);
	}

	public BuddyDTO(UserDTO user, String message, Status sendingStatus, Status receivingStatus)
	{
		this(null, user, message, null, null, sendingStatus, receivingStatus);
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

	Buddy createBuddyEntity(Translator translator)
	{
		return Buddy.createInstance(user.getID(), determineTempNickname(translator), getSendingStatus(), getReceivingStatus());
	}

	private String determineTempNickname(Translator translator)
	{
		// Used to till the user accepted the request and shared their nickname
		return translator.getLocalizedMessage("message.temp.nickname", user.getFirstName(), user.getLastName());
	}

	public static BuddyDTO createInstance(Buddy buddyEntity)
	{
		return new BuddyDTO(buddyEntity.getID(), UserDTO.createInstanceIfNotNull(buddyEntity.getUser()),
				buddyEntity.getNickname(), getBuddyUserAnonymizedID(buddyEntity), buddyEntity.getSendingStatus(),
				buddyEntity.getReceivingStatus());
	}

	private static UUID getBuddyUserAnonymizedID(Buddy buddyEntity)
	{
		return BuddyService.canIncludePrivateData(buddyEntity) ? buddyEntity.getUserAnonymizedID() : null;
	}

	@JsonInclude(Include.NON_EMPTY)
	public String getNickname()
	{
		return nickname;
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
	public UUID getUserAnonymizedID()
	{
		return userAnonymizedID;
	}

	public void setGoals(Set<GoalDTO> goals)
	{
		this.goals = goals;
	}

	@JsonIgnore
	public Set<GoalDTO> getGoals()
	{
		return goals;
	}
}
