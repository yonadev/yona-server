package nu.yona.server.device.service;

import java.util.UUID;

public abstract class DeviceBaseDto
{
	private final UUID id;
	private final String name;
	private final boolean isVpnConnected;

	protected DeviceBaseDto(UUID id, String name, boolean isVpnConnected)
	{
		this.id = id;
		this.name = name;
		this.isVpnConnected = isVpnConnected;
	}

	public UUID getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean isVpnConnected()
	{
		return isVpnConnected;
	}
}
