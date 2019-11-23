/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import nu.yona.server.Constants;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.util.TimeUtil;

@JsonRootName("user")
public class UserDto
{
	public static final String BUDDIES_REL_NAME = "buddies";
	public static final String GOALS_REL_NAME = "goals";
	public static final String DEVICES_REL_NAME = "devices";

	private UUID id;
	private final Optional<LocalDateTime> creationTime;
	private final Optional<LocalDate> appLastOpenedDate;
	private final String emailAddress;
	private final String mobileNumber;
	private final boolean isMobileNumberConfirmed;
	private final boolean isCreatedOnBuddyRequest;
	private final UserPrivateDataBaseDto privateData;

	private UserDto(UUID id, LocalDateTime creationTime, Optional<LocalDate> appLastOpenedDate,
			Optional<LocalDate> lastMonitoredActivityDate, String firstName, String lastName, String yonaPassword,
			String nickname, Optional<UUID> userPhotoId, String mobileNumber, boolean isConfirmed,
			boolean isCreatedOnBuddyRequest, UUID namedMessageSourceId, UUID anonymousMessageSourceId, Set<GoalDto> goals,
			Set<BuddyDto> buddies, UUID userAnonymizedId, Set<UserDeviceDto> devices)
	{
		this(id, Optional.of(creationTime), appLastOpenedDate, null, mobileNumber, isConfirmed, isCreatedOnBuddyRequest,
				new OwnUserPrivateDataDto(lastMonitoredActivityDate, yonaPassword, firstName, lastName, nickname, userPhotoId,
						namedMessageSourceId, anonymousMessageSourceId, goals, buddies, userAnonymizedId, devices));
	}

	private UserDto(UUID id, Optional<LocalDate> appLastOpenedDate, String mobileNumber, boolean isConfirmed,
			boolean isCreatedOnBuddyRequest)
	{
		this(id, Optional.empty(), appLastOpenedDate, null, mobileNumber, isConfirmed, isCreatedOnBuddyRequest, null);
	}

	@JsonCreator
	public UserDto(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
			@JsonProperty("emailAddress") String emailAddress, @JsonProperty("mobileNumber") String mobileNumber,
			@JsonProperty("nickname") String nickname)
	{
		this(null, Optional.empty(), null, emailAddress, mobileNumber, false /* default value, ignored */,
				false /* default value, ignored */,
				new OwnUserPrivateDataDto(firstName, lastName, nickname, Collections.emptySet()));
	}

	private UserDto(UUID id, Optional<LocalDateTime> creationTime, Optional<LocalDate> appLastOpenedDate, String emailAddress,
			String mobileNumber, boolean isMobileNumberConfirmed, boolean isCreatedOnBuddyRequest,
			UserPrivateDataBaseDto privateData)
	{
		this.id = id;
		this.creationTime = creationTime;
		this.appLastOpenedDate = appLastOpenedDate;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.isMobileNumberConfirmed = isMobileNumberConfirmed;
		this.isCreatedOnBuddyRequest = isCreatedOnBuddyRequest;
		this.privateData = privateData;
	}

	@JsonIgnore
	public UUID getId()
	{
		return id;
	}

	@JsonIgnore
	public void setUserId(UUID id)
	{
		this.id = id;
	}

	@JsonProperty("creationTime")
	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public Optional<ZonedDateTime> getCreationTimeAsZonedDateTime()
	{
		return creationTime.map(TimeUtil::toUtcZonedDateTime);
	}

	@JsonIgnore
	public Optional<LocalDateTime> getCreationTime()
	{
		return creationTime;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	@JsonInclude(Include.NON_EMPTY)
	public Optional<LocalDate> getAppLastOpenedDate()
	{
		return appLastOpenedDate;
	}

	@JsonInclude(Include.NON_EMPTY)
	public String getEmailAddress()
	{
		return emailAddress;
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}

	@JsonIgnore
	public boolean isMobileNumberConfirmed()
	{
		return isMobileNumberConfirmed;
	}

	@JsonIgnore
	public boolean isCreatedOnBuddyRequest()
	{
		return isCreatedOnBuddyRequest;
	}

	@JsonUnwrapped
	@JsonProperty(access = Access.READ_ONLY)
	public UserPrivateDataBaseDto getPrivateData()
	{
		return privateData;
	}

	@JsonIgnore
	public OwnUserPrivateDataDto getOwnPrivateData()
	{
		if (privateData instanceof OwnUserPrivateDataDto)
		{
			return (OwnUserPrivateDataDto) privateData;
		}
		throw new IllegalStateException("Cannot fetch own user private data. Private data "
				+ ((privateData == null) ? "is null" : "is of type " + privateData.getClass().getName()));
	}

	@JsonIgnore
	public UserPrivateDataBaseDto getBuddyPrivateData()
	{
		if (privateData instanceof BuddyUserPrivateDataDto)
		{
			return privateData;
		}
		throw new IllegalStateException("Cannot fetch buddy user private data. Private data "
				+ ((privateData == null) ? "is null" : " is of type " + privateData.getClass().getName()));
	}

	void updateUser(User originalUserEntity)
	{
		originalUserEntity.setFirstName(privateData.getFirstName());
		originalUserEntity.setLastName(privateData.getLastName());
		originalUserEntity.setMobileNumber(mobileNumber);
		originalUserEntity.setNickname(privateData.getNickname());
	}

	static UserDto createInstanceWithoutPrivateData(User userEntity)
	{
		Objects.requireNonNull(userEntity, "userEntity cannot be null");

		return new UserDto(userEntity.getId(), userEntity.getAppLastOpenedDate(), userEntity.getMobileNumber(),
				userEntity.isMobileNumberConfirmed(), userEntity.isCreatedOnBuddyRequest());
	}

	public static UserDto createInstance(User userEntity, Set<BuddyDto> buddies)
	{
		return new UserDto(userEntity.getId(), userEntity.getCreationTime(), userEntity.getAppLastOpenedDate(),
				userEntity.getLastMonitoredActivityDate(), userEntity.getFirstName(), userEntity.getLastName(),
				CryptoSession.getCurrent().getKeyString(), userEntity.getNickname(), userEntity.getUserPhotoId(),
				userEntity.getMobileNumber(), userEntity.isMobileNumberConfirmed(), userEntity.isCreatedOnBuddyRequest(),
				userEntity.getNamedMessageSourceId(), userEntity.getAnonymousMessageSourceId(),
				UserAnonymizedDto.getGoalsIncludingHistoryItems(userEntity.getAnonymized()), buddies,
				userEntity.getUserAnonymizedId(),
				userEntity.getDevices().stream().map(UserDeviceDto::createInstance).collect(Collectors.toSet()));
	}

	public static UserDto createInstance(User userEntity, BuddyUserPrivateDataDto buddyData)
	{
		return new UserDto(userEntity.getId(), Optional.empty(), userEntity.getAppLastOpenedDate(), null,
				userEntity.getMobileNumber(), userEntity.isMobileNumberConfirmed(), userEntity.isCreatedOnBuddyRequest(),
				buddyData);
	}

	public static UserDto createInstance(String firstName, String lastName, String mobileNumber, String nickname,
			Optional<UserDeviceDto> device)
	{
		UserPrivateDataBaseDto privateData = new OwnUserPrivateDataDto(firstName, lastName, nickname,
				device.map(Collections::singleton).orElse(Collections.emptySet()));
		return new UserDto(null, Optional.empty(), Optional.empty(), null, mobileNumber, false, false, privateData);
	}

	public void assertMobileNumberConfirmed()
	{
		if (!isMobileNumberConfirmed)
		{
			throw MobileNumberConfirmationException.notConfirmed(mobileNumber);
		}
	}
}
