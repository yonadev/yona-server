package nu.yona.server.device.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "BUDDY_DEVICES")
public class BuddyDevice extends DeviceBase
{
	// Default constructor is required for JPA
	public BuddyDevice()
	{
	}

	public BuddyDevice(UUID id, String name)
	{
		super(id, name);
	}

	public static BuddyDeviceRepository getRepository()
	{
		return (BuddyDeviceRepository) RepositoryProvider.getRepository(BuddyDevice.class, UUID.class);
	}
}