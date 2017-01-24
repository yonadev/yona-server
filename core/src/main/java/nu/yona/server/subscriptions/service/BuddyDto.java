/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.util.TimeUtil;

@JsonRootName("buddy")
public class BuddyDto
{
	public static final String USER_REL_NAME = "user";
	public static final String GOALS_REL_NAME = "goals";

	private final UUID id;
	private final UserDto user;
	private final String personalInvitationMessage;
	private final String nickname;
	private final Optional<UUID> userAnonymizedId;
	private final Status sendingStatus;
	private final Status receivingStatus;
	private Set<GoalDto> goals = Collections.emptySet();
	private final LocalDateTime lastStatusChangeTime;

	public BuddyDto(UUID id, UserDto user, String nickname, Optional<UUID> userAnonymizedId, Status sendingStatus,
			Status receivingStatus, LocalDateTime lastStatusChangeTime)
	{
		this(id, user, null, nickname, userAnonymizedId, sendingStatus, receivingStatus, lastStatusChangeTime);
	}

	public BuddyDto(UserDto user, String personalInvitationMessage, Status sendingStatus, Status receivingStatus,
			LocalDateTime lastStatusChangeTime)
	{
		this(null, user, personalInvitationMessage, null, null, sendingStatus, receivingStatus, lastStatusChangeTime);
	}

	private BuddyDto(UUID id, UserDto user, String personalInvitationMessage, String nickname, Optional<UUID> userAnonymizedId,
			Status sendingStatus, Status receivingStatus, LocalDateTime lastStatusChangeTime)
	{
		this.id = id;
		this.user = user;
		this.personalInvitationMessage = personalInvitationMessage;
		this.nickname = nickname;
		this.userAnonymizedId = userAnonymizedId;
		this.sendingStatus = sendingStatus;
		this.receivingStatus = receivingStatus;
		this.lastStatusChangeTime = lastStatusChangeTime;
	}

	@JsonIgnore
	public UUID getId()
	{
		return id;
	}

	@JsonIgnore
	public String getPersonalInvitationMessage()
	{
		return personalInvitationMessage;
	}

	@JsonIgnore
	public UserDto getUser()
	{
		return user;
	}

	Buddy createBuddyEntity(Translator translator)
	{
		return Buddy.createInstance(user.getId(), determineTempNickname(translator), getSendingStatus(), getReceivingStatus());
	}

	private String determineTempNickname(Translator translator)
	{
		// Used to till the user accepted the request and shared their nickname
		return translator.getLocalizedMessage("message.temp.nickname", user.getFirstName(), user.getLastName());
	}

	public static BuddyDto createInstance(Buddy buddyEntity)
	{
		return new BuddyDto(buddyEntity.getId(), UserDto.createInstance(buddyEntity.getUser()), buddyEntity.getNickname(),
				getBuddyUserAnonymizedId(buddyEntity), buddyEntity.getSendingStatus(), buddyEntity.getReceivingStatus(),
				buddyEntity.getLastStatusChangeTime());
	}

	private static Optional<UUID> getBuddyUserAnonymizedId(Buddy buddyEntity)
	{
		return BuddyService.canIncludePrivateData(buddyEntity) ? buddyEntity.getUserAnonymizedId() : Optional.empty();
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
	public Optional<UUID> getUserAnonymizedId()
	{
		return userAnonymizedId;
	}

	public void setGoals(Set<GoalDto> goals)
	{
		this.goals = goals;
	}

	@JsonIgnore
	public Set<GoalDto> getGoals()
	{
		return goals;
	}
}
