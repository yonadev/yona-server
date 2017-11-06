package nu.yona.server.device.entities;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "DEVICES_ANONYMIZED")
public abstract class DeviceAnonymized extends EntityWithUuid
{
	private int deviceId;

	private LocalDate lastMonitoredActivityDate;

	@ManyToOne
	private UserAnonymized userAnonymized;

	// Default constructor is required for JPA
	protected DeviceAnonymized()
	{
		super(null);
	}

	protected DeviceAnonymized(UUID id, UserAnonymized userAnonymized, int deviceId)
	{
		super(id);
		Objects.requireNonNull(userAnonymized);
		this.userAnonymized = userAnonymized;
		this.deviceId = deviceId;
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	public void setUserAnonymized(UserAnonymized userAnonymized)
	{
		this.userAnonymized = userAnonymized;
	}

	public void clearUserAnonymized()
	{
		this.userAnonymized = null;
	}

	public int getDeviceId()
	{
		return deviceId;
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		Objects.requireNonNull(lastMonitoredActivityDate);
		this.lastMonitoredActivityDate = lastMonitoredActivityDate;
	}

	public String getVpnLoginId()
	{
		return userAnonymized.getId() + "-" + getDeviceId();
	}
}