package nu.yona.server.device.entities;

import java.util.UUID;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.subscriptions.entities.EntityWithUuidAndTouchVersion;

@Entity
@Table(name = "DEVICES")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class DeviceBase extends EntityWithUuidAndTouchVersion
{
	@Convert(converter = StringFieldEncryptor.class)
	private String name;

	// Not encrypted, as it cannot be used to break anonymity
	private boolean isVpnConnected;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID deviceAnonymizedId;

	// Default constructor is required for JPA
	protected DeviceBase()
	{
		super(null);
	}

	protected DeviceBase(UUID id, String name)
	{
		super(id);
		this.setName(name);
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public boolean isVpnConnected()
	{
		return isVpnConnected;
	}

	public void setVpnConnected(boolean isVpnConnected)
	{
		this.isVpnConnected = isVpnConnected;
	}

	public UUID getDeviceAnonymizedId()
	{
		return deviceAnonymizedId;
	}

	public void setDeviceAnonymizedId(UUID deviceAnonymizedId)
	{
		this.deviceAnonymizedId = deviceAnonymizedId;
	}

}
