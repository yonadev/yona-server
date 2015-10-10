/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("User")
public class UserDTO {
	private final UUID id;
	private final String firstName;
	private final String lastName;
	private final String emailAddress;
	private final String mobileNumber;
	private final UserPrivateDTO privateData;

	private UserDTO(UUID id, String firstName, String lastName, String nickName, String emailAddress,
			String mobileNumber, Set<String> deviceNames, Set<String> goalNames, VPNProfileDTO vpnProfile) {
		this(id, firstName, lastName, emailAddress, mobileNumber,
				new UserPrivateDTO(nickName, deviceNames, goalNames, vpnProfile));
	}

	private UserDTO(UUID id, String firstName, String lastName, String emailAddress, String mobileNumber) {
		this(id, firstName, lastName, emailAddress, mobileNumber, null);
	}

	@JsonCreator
	public UserDTO(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
			@JsonProperty("emailAddress") String emailAddress, @JsonProperty("mobileNumber") String mobileNumber,
			@JsonUnwrapped UserPrivateDTO privateData) {
		this(null, firstName, lastName, emailAddress, mobileNumber, privateData);
	}

	private UserDTO(UUID id, String firstName, String lastName, String emailAddress, String mobileNumber,
			UserPrivateDTO privateData) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.privateData = privateData;
	}

	@JsonIgnore
	public UUID getID() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	@JsonUnwrapped
	public UserPrivateDTO getPrivateData() {
		return privateData;
	}

	User createUserEntity() {
		return User.createInstance(firstName, lastName, privateData.getNickName(), emailAddress, mobileNumber,
				privateData.getDeviceNames(), privateData.getGoals());
	}

	User updateUser(User originalUserEntity) {
		originalUserEntity.setFirstName(firstName);
		originalUserEntity.setLastName(lastName);
		originalUserEntity.setNickName(privateData.getNickName());
		originalUserEntity.setEmailAddress(emailAddress);
		originalUserEntity.setMobileNumber(mobileNumber);
		originalUserEntity.setDeviceNames(privateData.getDeviceNames());

		return originalUserEntity;
	}

	private static Set<String> getGoalNames(Set<Goal> goals) {
		return goals.stream().map(Goal::getName).collect(toSet());
	}

	static UserDTO createInstance(User userEntity) {
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(),
				userEntity.getEmailAddress(), userEntity.getMobileNumber());
	}

	static UserDTO createInstanceWithPrivateData(User userEntity) {
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(),
				userEntity.getNickName(), userEntity.getEmailAddress(), userEntity.getMobileNumber(),
				userEntity.getDeviceNames(), getGoalNames(userEntity.getGoals()),
				VPNProfileDTO.createInstance(userEntity.getVPNProfile()));
	}
}
