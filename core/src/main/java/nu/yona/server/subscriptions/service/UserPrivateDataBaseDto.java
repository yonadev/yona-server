package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;

public class UserPrivateDataBaseDto
{
	private final String nickname;
	private final Set<GoalDto> goals;
	private final Set<DeviceBaseDto> devices;

	protected UserPrivateDataBaseDto(String nickname, Set<GoalDto> goals, Set<DeviceBaseDto> devices)
	{
		this.devices = devices;
		Objects.requireNonNull(goals);
		this.nickname = nickname;
		this.goals = goals;
	}

	public String getNickname()
	{
		return nickname;
	}

	@JsonIgnore
	public Set<GoalDto> getGoals()
	{
		return Collections.unmodifiableSet(goals);
	}

	@JsonIgnore
	public Set<DeviceBaseDto> getDevices()
	{
		return devices;
	}

}