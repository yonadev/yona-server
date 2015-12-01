/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("user")
public class UserDTO
{
	public static final String BUDDIES_REL_NAME = "buddies";
	private UUID id;
	private final String firstName;
	private final String lastName;
	private final String emailAddress;
	private final String mobileNumber;
	private boolean isMobileNumberConfirmed;
	private final UserPrivateDTO privateData;
	/*
	 * Only intended for test purposes.
	 */
	private String confirmationCode;

	private UserDTO(UUID id, String firstName, String lastName, String nickname, String mobileNumber, boolean isConfirmed,
			UUID namedMessageSourceID, UUID namedMessageDestinationID, UUID anonymousMessageSourceID,
			UUID anonymousMessageDestinationID, Set<String> deviceNames, Set<String> goalNames, Set<UUID> buddyIDs,
			VPNProfileDTO vpnProfile)
	{
		this(id, firstName, lastName, null, mobileNumber, isConfirmed,
				new UserPrivateDTO(nickname, namedMessageSourceID, namedMessageDestinationID, anonymousMessageSourceID,
						anonymousMessageDestinationID, deviceNames, goalNames, buddyIDs, vpnProfile));
	}

	private UserDTO(UUID id, String firstName, String lastName, String mobileNumber, boolean isConfirmed)
	{
		this(id, firstName, lastName, null, mobileNumber, isConfirmed, null);
	}

	@JsonCreator
	public UserDTO(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
			@JsonProperty("emailAddress") String emailAddress, @JsonProperty("mobileNumber") String mobileNumber,
			@JsonProperty("isConfirmed") boolean isConfirmed, @JsonUnwrapped UserPrivateDTO privateData)
	{
		this(null, firstName, lastName, emailAddress, mobileNumber, isConfirmed, privateData);
	}

	private UserDTO(UUID id, String firstName, String lastName, String emailAddress, String mobileNumber,
			boolean isMobileNumberConfirmed, UserPrivateDTO privateData)
	{
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.isMobileNumberConfirmed = isMobileNumberConfirmed;
		this.privateData = privateData;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonIgnore
	public void setUserID(UUID id)
	{
		this.id = id;
	}

	public String getFirstName()
	{
		return firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public String getEmailAddress()
	{
		return emailAddress;
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}

	public boolean isMobileNumberConfirmed()
	{
		return isMobileNumberConfirmed;
	}

	@JsonUnwrapped
	public UserPrivateDTO getPrivateData()
	{
		return privateData;
	}

	/*
	 * Only intended for test purposes.
	 */
	public void setConfirmationCode(String confirmationCode)
	{
		this.confirmationCode = confirmationCode;
	}

	/*
	 * Only intended for test purposes.
	 */
	@JsonInclude(Include.NON_EMPTY)
	public String getConfirmationCode()
	{
		return confirmationCode;
	}

	User createUserEntity()
	{
		return User.createInstance(firstName, lastName, privateData.getNickName(), mobileNumber,
				privateData.getVpnProfile().getVpnPassword(), privateData.getDeviceNames(), privateData.getGoals());
	}

	User updateUser(User originalUserEntity)
	{
		originalUserEntity.setFirstName(firstName);
		originalUserEntity.setLastName(lastName);
		originalUserEntity.setMobileNumber(mobileNumber);
		originalUserEntity.setNickname(privateData.getNickName());
		originalUserEntity.setDeviceNames(privateData.getDeviceNames());

		return originalUserEntity;
	}

	private static Set<String> getGoalNames(Set<Goal> goals)
	{
		return goals.stream().map(Goal::getName).collect(toSet());
	}

	static UserDTO createInstance(User userEntity)
	{
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(), userEntity.getMobileNumber(),
				userEntity.isMobileNumberConfirmed());
	}

	static UserDTO createRemovedUserInstance()
	{
		return new UserDTO(null, null, null, null, false);
	}

	static UserDTO createInstanceWithPrivateData(User userEntity)
	{
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(), userEntity.getNickName(),
				userEntity.getMobileNumber(), userEntity.isMobileNumberConfirmed(), userEntity.getNamedMessageSource().getID(),
				userEntity.getNamedMessageDestination().getID(), userEntity.getAnonymousMessageSource().getID(),
				userEntity.getAnonymousMessageSource().getDestination().getID(), userEntity.getDeviceNames(),
				getGoalNames(userEntity.getGoals()), getBuddyIDs(userEntity), VPNProfileDTO.createInstance(userEntity));
	}

	private static Set<UUID> getBuddyIDs(User userEntity)
	{
		return userEntity.getBuddies().stream().map(b -> b.getID()).collect(Collectors.toSet());
	}

	public void assertMobileNumberConfirmed()
	{
		if (!isMobileNumberConfirmed)
		{
			throw MobileNumberConfirmationException.notConfirmed();
		}
	}
}
