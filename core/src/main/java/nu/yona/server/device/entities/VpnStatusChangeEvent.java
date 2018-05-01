/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.time.LocalDateTime;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithId;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "vpn_status_change_events")
public class VpnStatusChangeEvent extends EntityWithId
{
	private LocalDateTime eventTime;
	private boolean isVpnConnected;

	@ManyToOne
	private DeviceAnonymized deviceAnonymized;

	// Default constructor is required for JPA
	public VpnStatusChangeEvent()
	{
	}

	private VpnStatusChangeEvent(LocalDateTime eventTime, boolean isVpnConnected)
	{
		this.eventTime = eventTime;
		this.isVpnConnected = isVpnConnected;
	}

	public static VpnStatusChangeEvent createInstance(boolean isVpnConnected)
	{
		return new VpnStatusChangeEvent(TimeUtil.utcNow(), isVpnConnected);
	}

	public LocalDateTime getEventTime()
	{
		return eventTime;
	}

	public boolean isVpnConnected()
	{
		return isVpnConnected;
	}

	public void setDeviceAnonymized(DeviceAnonymized deviceAnonymized)
	{
		this.deviceAnonymized = deviceAnonymized;
	}
}
