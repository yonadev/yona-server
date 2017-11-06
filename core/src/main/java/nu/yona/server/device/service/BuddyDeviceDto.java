package nu.yona.server.device.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.device.entities.BuddyDevice;

@JsonRootName("device")
public class BuddyDeviceDto extends DeviceBaseDto
{
	private BuddyDeviceDto(UUID id, String name, boolean isVpnConnected)
	{
		super(id, name, isVpnConnected);
	}

	public static BuddyDeviceDto createInstance(BuddyDevice deviceEntity)
	{
		return new BuddyDeviceDto(deviceEntity.getId(), deviceEntity.getName(), deviceEntity.isVpnConnected());
	}
}
