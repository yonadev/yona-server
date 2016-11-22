/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.Translator;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.util.TimeUtil;

@JsonRootName("buddy")
public class BuddyDTO
{
	public static final String USER_REL_NAME = "user";
	public static final String GOALS_REL_NAME = "goals";

	private final UUID id;
	private final UserDTO user;
	private final String message;
	private final String nickname;
	private final Optional<UUID> userAnonymizedID;
	private final Status sendingStatus;
	private final Status receivingStatus;
	private Set<GoalDTO> goals = Collections.emptySet();
	private final LocalDateTime lastStatusChangeTime;

	public BuddyDTO(UUID id, UserDTO user, String nickname, Optional<UUID> userAnonymizedID, Status sendingStatus,
			Status receivingStatus, LocalDateTime lastStatusChangeTime)
	{
		this(id, user, null, nickname, userAnonymizedID, sendingStatus, receivingStatus, lastStatusChangeTime);
	}

	public BuddyDTO(UserDTO user, String message, Status sendingStatus, Status receivingStatus,
			LocalDateTime lastStatusChangeTime)
	{
		this(null, user, message, null, null, sendingStatus, receivingStatus, lastStatusChangeTime);
	}

	private BuddyDTO(UUID id, UserDTO user, String message, String nickname, Optional<UUID> userAnonymizedID,
			Status sendingStatus, Status receivingStatus, LocalDateTime lastStatusChangeTime)
	{
		this.id = id;
		this.user = user;
		this.message = message;
		this.nickname = nickname;
		this.userAnonymizedID = userAnonymizedID;
		this.sendingStatus = sendingStatus;
		this.receivingStatus = receivingStatus;
		this.lastStatusChangeTime = lastStatusChangeTime;
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
		return new BuddyDTO(buddyEntity.getID(), UserDTO.createInstance(buddyEntity.getUser()), buddyEntity.getNickname(),
				getBuddyUserAnonymizedID(buddyEntity), buddyEntity.getSendingStatus(), buddyEntity.getReceivingStatus(),
				buddyEntity.getLastStatusChangeTime());
	}

	private static Optional<UUID> getBuddyUserAnonymizedID(Buddy buddyEntity)
	{
		return BuddyService.canIncludePrivateData(buddyEntity) ? buddyEntity.getUserAnonymizedID() : Optional.empty();
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

	@JsonProperty("lastStatusChangeTime")
	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public ZonedDateTime getLastStatusChangeTimeAsZonedDateTime()
	{
		return TimeUtil.toUtcZonedDateTime(lastStatusChangeTime);
	}

	@JsonIgnore
	public LocalDateTime getLastStatusChangeTime()
	{
		return lastStatusChangeTime;
	}

	@JsonIgnore
	public Optional<UUID> getUserAnonymizedID()
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
