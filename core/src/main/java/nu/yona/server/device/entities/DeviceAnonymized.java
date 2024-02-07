/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinFormula;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "DEVICES_ANONYMIZED")
public class DeviceAnonymized extends EntityWithUuid
{
	public enum OperatingSystem
	{
		UNKNOWN, ANDROID, IOS
	}

	@ManyToOne
	private UserAnonymized userAnonymized;

	private int deviceIndex;

	@JdbcType(value = IntegerJdbcType.class)
	private OperatingSystem operatingSystem;

	private LocalDate lastMonitoredActivityDate;

	private String appVersion;

	private int appVersionCode;

	private String firebaseInstanceId;

	private Locale locale;

	@OneToMany(mappedBy = "deviceAnonymized", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<VpnStatusChangeEvent> vpnStatusChangeEvents;

	@ManyToOne
	@JoinColumnOrFormula(formula = @JoinFormula(value = "(SELECT e.id FROM vpn_status_change_events e WHERE e.device_anonymized_id = id ORDER BY e.id DESC LIMIT 1)", referencedColumnName = "id"))
	private VpnStatusChangeEvent lastVpnStatusChangeEvent;

	// Default constructor is required for JPA
	protected DeviceAnonymized()
	{
		super(null);
	}

	private DeviceAnonymized(UUID id, int deviceIndex, OperatingSystem operatingSystem, String appVersion, int appVersionCode,
			Optional<String> firebaseInstanceId, Locale locale)
	{
		super(id);
		this.deviceIndex = deviceIndex;
		this.operatingSystem = operatingSystem;
		this.appVersion = Objects.requireNonNull(appVersion);
		this.appVersionCode = appVersionCode;
		this.firebaseInstanceId = firebaseInstanceId.orElse(null);
		this.locale = locale;
	}

	public static DeviceAnonymized createInstance(int deviceIndex, OperatingSystem operatingSystem, String appVersion,
			int appVersionCode, Optional<String> firebaseInstanceId, Locale locale)
	{
		return new DeviceAnonymized(UUID.randomUUID(), deviceIndex, operatingSystem, appVersion, appVersionCode,
				firebaseInstanceId, locale);
	}

	public static DeviceAnonymizedRepository getRepository()
	{
		return (DeviceAnonymizedRepository) RepositoryProvider.getRepository(DeviceAnonymized.class, UUID.class);
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	public void setUserAnonymized(UserAnonymized userAnonymized)
	{
		this.userAnonymized = Objects.requireNonNull(userAnonymized);
	}

	public void clearUserAnonymized()
	{
		this.userAnonymized = null;
	}

	public int getDeviceIndex()
	{
		return deviceIndex;
	}

	public OperatingSystem getOperatingSystem()
	{
		return operatingSystem;
	}

	public void setOperatingSystem(OperatingSystem operatingSystem)
	{
		this.operatingSystem = operatingSystem;
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		this.lastMonitoredActivityDate = Objects.requireNonNull(lastMonitoredActivityDate);
	}

	public String getAppVersion()
	{
		return appVersion;
	}

	public int getAppVersionCode()
	{
		return appVersionCode;
	}

	public void updateAppVersion(String appVersion, int appVersionCode)
	{
		this.appVersion = Objects.requireNonNull(appVersion);
		this.appVersionCode = appVersionCode;
	}

	public String getVpnLoginId()
	{
		return userAnonymized.getId() + "-" + getDeviceIndex();
	}

	public void addVpnStatusChangeEvent(VpnStatusChangeEvent event)
	{
		vpnStatusChangeEvents.add(event);
		event.setDeviceAnonymized(this);
		lastVpnStatusChangeEvent = event;
	}

	public Set<VpnStatusChangeEvent> getVpnStatusChangeEvents()
	{
		return Collections.unmodifiableSet(vpnStatusChangeEvents);
	}

	public Optional<VpnStatusChangeEvent> getLastVpnStatusChangeEvent()
	{
		return Optional.ofNullable(lastVpnStatusChangeEvent);
	}

	public Optional<String> getFirebaseInstanceId()
	{
		return Optional.ofNullable(firebaseInstanceId);
	}

	public void setFirebaseInstanceId(String firebaseInstanceId)
	{
		this.firebaseInstanceId = Objects.requireNonNull(firebaseInstanceId);
	}

	public void clearFirebaseInstanceId()
	{
		this.firebaseInstanceId = null;
	}

	public Locale getLocale()
	{
		return locale;
	}

	public void setLocale(Locale locale)
	{
		this.locale = locale;
	}
}