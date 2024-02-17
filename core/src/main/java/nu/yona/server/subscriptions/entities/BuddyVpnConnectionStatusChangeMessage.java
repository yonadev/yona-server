/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
@DiscriminatorValue("BuddyVpnConnectionStatChngMsg") // Default max length = 31
public class BuddyVpnConnectionStatusChangeMessage extends BuddyMessage
{
	private boolean isVpnConnected;

	@Transient
	private UUID deviceAnonymizedId;
	private byte[] deviceAnonymizedIdCiphertext;

	// Default constructor is required for JPA
	public BuddyVpnConnectionStatusChangeMessage()
	{
		super();
	}

	private BuddyVpnConnectionStatusChangeMessage(BuddyInfoParameters buddyInfoParameters, String messageText,
			boolean isVpnConnected, UUID deviceAnonymizedId)
	{
		super(buddyInfoParameters, messageText);
		this.isVpnConnected = isVpnConnected;
		this.deviceAnonymizedId = deviceAnonymizedId;
	}

	public static BuddyVpnConnectionStatusChangeMessage createInstance(BuddyInfoParameters buddyInfoParameters,
			String messageText, boolean isVpnConnected, UUID deviceAnonymizedId)
	{
		return new BuddyVpnConnectionStatusChangeMessage(buddyInfoParameters, messageText, isVpnConnected, deviceAnonymizedId);
	}

	public boolean isVpnConnected()
	{
		return isVpnConnected;
	}

	public UUID getDeviceAnonymizedId()
	{
		return deviceAnonymizedId;
	}

	@Override
	public void encrypt()
	{
		super.encrypt();
		deviceAnonymizedIdCiphertext = SecretKeyUtil.encryptUuid(getDeviceAnonymizedId());
	}

	@Override
	public void decrypt()
	{
		super.decrypt();
		this.deviceAnonymizedId = SecretKeyUtil.decryptUuid(deviceAnonymizedIdCiphertext);
	}
}
