/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static java.util.stream.Collectors.toSet;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.rest.BadRequestException;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("User")
public class UserDTO {
	private static final Logger LOGGER = Logger.getLogger(UserDTO.class.getName());
	private final UUID id;
	private final String firstName;
	private final String lastName;
	private final String nickName;
	private final String emailAddress;
	private final String mobileNumber;
	private final Set<String> deviceNames;
	private Set<String> goalNames;
	private final String password;

	private UserDTO(UUID id, String firstName, String lastName, String nickName, String emailAddress,
			String mobileNumber, Set<String> deviceNames, Set<String> goalNames, String password) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.nickName = nickName;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.deviceNames = (deviceNames == null) ? Collections.emptySet() : deviceNames;
		this.goalNames = (goalNames == null) ? Collections.emptySet() : goalNames;
		this.password = password;
	}

	@JsonCreator
	public UserDTO(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
			@JsonProperty("nickName") String nickName, @JsonProperty("emailAddress") String emailAddress,
			@JsonProperty("mobileNumber") String mobileNumber,
			@JsonProperty("devices") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> deviceNames,
			@JsonProperty("goals") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> goalNames,
			@JsonProperty("password") String password) {
		this(null, firstName, lastName, nickName, emailAddress, mobileNumber, deviceNames, goalNames, password);
	}

	private UserDTO(UUID id, String firstName, String lastName, String emailAddress, String mobileNumber) {
		this(id, firstName, lastName, null, emailAddress, mobileNumber, Collections.emptySet(), Collections.emptySet(),
				null);
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

	@JsonInclude(Include.NON_EMPTY)
	public String getNickName() {
		return nickName;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	@JsonProperty("devices")
	@JsonInclude(Include.NON_EMPTY)
	public Set<String> getDeviceNames() {
		return Collections.unmodifiableSet(deviceNames);
	}

	@JsonProperty("goals")
	@JsonInclude(Include.NON_EMPTY)
	public Set<String> getGoalNames() {
		return Collections.unmodifiableSet(goalNames);
	}

	@JsonInclude(Include.NON_EMPTY)
	public String getPassword() {
		return password;
	}

	User createUserEntity() {
		return User.createInstance(firstName, lastName, nickName, emailAddress, mobileNumber, deviceNames, getGoals());
	}

	User updateUser(User originalUserEntity) {
		originalUserEntity.setFirstName(firstName);
		originalUserEntity.setLastName(lastName);
		originalUserEntity.setNickName(nickName);
		originalUserEntity.setEmailAddress(emailAddress);
		originalUserEntity.setMobileNumber(mobileNumber);
		originalUserEntity.setDeviceNames(deviceNames);

		return originalUserEntity;
	}

	private Set<Goal> getGoals() {
		return goalNames.stream().map(n -> findGoal(n)).collect(toSet());
	}

	private Goal findGoal(String goalName) {
		Goal goal = Goal.getRepository().findByName(goalName);
		if (goal == null) {
			String message = "Goal '" + goalName + "' does not exist";
			LOGGER.warning(message);
			throw new BadRequestException(message);
		}

		return goal;

	}

	private static Set<String> getGoalNames(Set<Goal> goals) {
		return goals.stream().map(Goal::getName).collect(toSet());
	}

	static UserDTO createMinimallyInitializedInstance(User userEntity) {
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(),
				userEntity.getEmailAddress(), userEntity.getMobileNumber());
	}

	static UserDTO createFullyInitializedInstance(User userEntity) {
		return new UserDTO(userEntity.getID(), userEntity.getFirstName(), userEntity.getLastName(),
				userEntity.getNickName(), userEntity.getEmailAddress(), userEntity.getMobileNumber(),
				userEntity.getDeviceNames(), getGoalNames(userEntity.getGoals()), null);
	}
}
