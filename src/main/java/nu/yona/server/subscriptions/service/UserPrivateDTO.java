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
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.rest.BadRequestException;

public class UserPrivateDTO {
	private static final Logger LOGGER = Logger.getLogger(UserPrivateDTO.class.getName());
	private final String nickName;
	private final Set<String> deviceNames;
	private Set<String> goalNames;
	private VPNProfileDTO vpnProfile;

	@JsonCreator
	public UserPrivateDTO(@JsonProperty("nickName") String nickName,
			@JsonProperty("devices") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> deviceNames,
			@JsonProperty("goals") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> goalNames) {
		this(nickName, deviceNames, goalNames, null);
	}

	UserPrivateDTO(String nickName, Set<String> deviceNames, Set<String> goalNames, VPNProfileDTO vpnProfile) {
		this.nickName = nickName;
		this.deviceNames = (deviceNames == null) ? Collections.emptySet() : deviceNames;
		this.goalNames = (goalNames == null) ? Collections.emptySet() : goalNames;
		this.vpnProfile = vpnProfile;
	}

	public String getNickName() {
		return nickName;
	}

	@JsonProperty("devices")
	public Set<String> getDeviceNames() {
		return Collections.unmodifiableSet(deviceNames);
	}

	@JsonProperty("goals")
	public Set<String> getGoalNames() {
		return Collections.unmodifiableSet(goalNames);
	}

	public VPNProfileDTO getVpnProfile() {
		return vpnProfile;
	}

	Set<Goal> getGoals() {
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
}
