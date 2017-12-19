/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
	private final String firstName;
	private final String lastName;
	private final String emailAddress;
	private final String mobileNumber;
	private final boolean isMobileNumberConfirmed;
	private final UserPrivateDataBaseDto privateData;

	private UserDto(UUID id, LocalDateTime creationTime, Optional<LocalDate> appLastOpenedDate,
			Optional<LocalDate> lastMonitoredActivityDate, String firstName, String lastName, String yonaPassword,
			String nickname, Optional<UUID> userPhotoId, String mobileNumber, boolean isConfirmed, UUID namedMessageSourceId,
			UUID anonymousMessageSourceId, Set<GoalDto> goals, Set<BuddyDto> buddies, UUID userAnonymizedId,
			VPNProfileDto vpnProfile, Set<UserDeviceDto> devices)
	{
		this(id, Optional.of(creationTime), appLastOpenedDate, firstName, lastName, null, mobileNumber, isConfirmed,
				new OwnUserPrivateDataDto(lastMonitoredActivityDate, yonaPassword, nickname, userPhotoId, namedMessageSourceId,
						anonymousMessageSourceId, goals, buddies, userAnonymizedId, vpnProfile, devices));
	}

	private UserDto(UUID id, LocalDateTime creationTime, Optional<LocalDate> appLastOpenedDate, String firstName, String lastName,
			String mobileNumber, boolean isConfirmed)
	{
		this(id, Optional.of(creationTime), appLastOpenedDate, firstName, lastName, null, mobileNumber, isConfirmed, null);
	}

	@JsonCreator
	public UserDto(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
			@JsonProperty("emailAddress") String emailAddress, @JsonProperty("mobileNumber") String mobileNumber,
			@JsonProperty("nickname") String nickname)
	{
		this(null, Optional.empty(), null, firstName, lastName, emailAddress, mobileNumber, false /* default value, ignored */,
				new OwnUserPrivateDataDto(nickname, Collections.emptySet()));
	}

	private UserDto(UUID id, Optional<LocalDateTime> creationTime, Optional<LocalDate> appLastOpenedDate, String firstName,
			String lastName, String emailAddress, String mobileNumber, boolean isMobileNumberConfirmed,
			UserPrivateDataBaseDto privateData)
	{
		this.id = id;
		this.creationTime = creationTime;
		this.appLastOpenedDate = appLastOpenedDate;
		this.firstName = firstName;
		this.lastName = lastName;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.isMobileNumberConfirmed = isMobileNumberConfirmed;
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

	public String getFirstName()
	{
		return firstName;
	}

	public String getLastName()
	{
		return lastName;
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

	@JsonUnwrapped
	@JsonProperty(access = Access.READ_ONLY)
	public UserPrivateDataBaseDto getPrivateData()
	{
		return privateData;
	}

	@JsonIgnore
	public OwnUserPrivateDataDto getOwnPrivateData()
	{
		if (privateData == null)
		{
			return null; // TODO: Remove this when we don't support fetching the public user anymore
		}
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

	User updateUser(User originalUserEntity)
	{
		originalUserEntity.setFirstName(firstName);
		originalUserEntity.setLastName(lastName);
		originalUserEntity.setMobileNumber(mobileNumber);
		originalUserEntity.setNickname(privateData.getNickname());
		originalUserEntity.setUserPhotoId(privateData.getUserPhotoId());

		return originalUserEntity;
	}

	/**
	 * Creates a {@link UserDto} with the public data of the user, if the user is not {@code null}. This method is provided
	 * because the related user may be removed in the meantime. In that case the passed {@code userEntity} will be {@code null}
	 * and the method returns {@code null}.
	 * 
	 * @param userEntity
	 * @return
	 */
	public static Optional<UserDto> createInstance(Optional<User> userEntity)
	{
		return userEntity.map(UserDto::createInstance);
	}

	/**
	 * Creates a {@link UserDto} with the public data of the user. Please use {@link #createInstance(User)} if {@code userEntity}
	 * may be {@code null}. Use {@link #createInstanceWithPrivateData(User)} to include private data of the user.
	 * 
	 * @param userEntity
	 * @return
	 */
	static UserDto createInstance(User userEntity)
	{
		Objects.requireNonNull(userEntity, "userEntity cannot be null");

		return new UserDto(userEntity.getId(), userEntity.getCreationTime(), userEntity.getAppLastOpenedDate(),
				userEntity.getFirstName(), userEntity.getLastName(), userEntity.getMobileNumber(),
				userEntity.isMobileNumberConfirmed());
	}

	public static UserDto createInstanceWithPrivateData(User userEntity, Set<BuddyDto> buddies)
	{
		return new UserDto(userEntity.getId(), userEntity.getCreationTime(), userEntity.getAppLastOpenedDate(),
				userEntity.getLastMonitoredActivityDate(), userEntity.getFirstName(), userEntity.getLastName(),
				CryptoSession.getCurrent().getKeyString(), userEntity.getNickname(), userEntity.getUserPhotoId(),
				userEntity.getMobileNumber(), userEntity.isMobileNumberConfirmed(), userEntity.getNamedMessageSourceId(),
				userEntity.getAnonymousMessageSourceId(),
				UserAnonymizedDto.getGoalsIncludingHistoryItems(userEntity.getAnonymized()), buddies,
				userEntity.getUserAnonymizedId(), VPNProfileDto.createInstance(userEntity),
				userEntity.getDevices().stream().map(UserDeviceDto::createInstance).collect(Collectors.toSet()));
	}

	static UserDto createInstanceWithBuddyData(User userEntity, BuddyUserPrivateDataDto buddyData)
	{
		return new UserDto(userEntity.getId(), Optional.of(userEntity.getCreationTime()), userEntity.getAppLastOpenedDate(),
				userEntity.getFirstName(), userEntity.getLastName(), null, userEntity.getMobileNumber(),
				userEntity.isMobileNumberConfirmed(), buddyData);
	}

	public static UserDto createInstance(String firstName, String lastName, String mobileNumber, String nickname,
			Optional<UserDeviceDto> device)
	{
		UserPrivateDataBaseDto privateData = new OwnUserPrivateDataDto(nickname,
				device.map(Collections::singleton).orElse(Collections.emptySet()));
		return new UserDto(null, Optional.empty(), Optional.empty(), firstName, lastName, null, mobileNumber, false, privateData);
	}

	public void assertMobileNumberConfirmed()
	{
		if (!isMobileNumberConfirmed)
		{
			throw MobileNumberConfirmationException.notConfirmed(mobileNumber);
		}
	}
}
