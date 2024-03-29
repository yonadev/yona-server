/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.service.DeviceRequestException;
import nu.yona.server.util.TimeUtil;

/*
 * A request to add another device for an existing user. The data cannot be encrypted with the 'yona password' key because that is
 * stored on the device and cannot be entered by the user. Therefore we have to transfer the 'yona password' over the server to
 * the new device and obviously encrypt it (with a temporary password entered by the user, e.g. a one time password generated by
 * the app) while stored on the server.
 */
@Entity
@Table(name = "NEW_DEVICE_REQUESTS")
public class NewDeviceRequest extends EntityWithUuid
{
	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Transient
	private String decryptionCheck;
	private String decryptionCheckCipherText;

	private LocalDateTime creationTime;

	@Transient
	private String yonaPassword;
	private String yonaPasswordCipherText;

	private byte[] initializationVector;

	// Default constructor is required for JPA
	public NewDeviceRequest()
	{
		super(null);
	}

	private NewDeviceRequest(UUID id, String yonaPassword, LocalDateTime creationDateTime)
	{
		super(id);
		this.yonaPassword = yonaPassword;
		this.creationTime = creationDateTime;
		this.decryptionCheck = buildDecryptionCheck();
	}

	public static NewDeviceRequestRepository getRepository()
	{
		return (NewDeviceRequestRepository) RepositoryProvider.getRepository(NewDeviceRequest.class, UUID.class);
	}

	public static NewDeviceRequest createInstance(String yonaPassword)
	{
		return new NewDeviceRequest(UUID.randomUUID(), yonaPassword, TimeUtil.utcNow());
	}

	public LocalDateTime getCreationTime()
	{
		return creationTime;
	}

	public String getYonaPassword()
	{
		return yonaPassword;
	}

	private static String buildDecryptionCheck()
	{
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	private boolean isDecrypted()
	{
		return decryptionCheck != null;
	}

	public boolean isDecryptedProperly()
	{
		return isDecrypted() && decryptionCheck.startsWith(DECRYPTION_CHECK_STRING);
	}

	public void encryptYonaPassword(String newDeviceRequestPassword)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(newDeviceRequestPassword))
		{
			this.initializationVector = CryptoSession.getCurrent().generateInitializationVector();
			CryptoSession.getCurrent().setInitializationVector(this.initializationVector);
			this.yonaPasswordCipherText = new StringFieldEncryptor().convertToDatabaseColumn(this.yonaPassword);
			this.decryptionCheckCipherText = new StringFieldEncryptor().convertToDatabaseColumn(this.decryptionCheck);
		}
	}

	public void decryptYonaPassword(String newDeviceRequestPassword, String mobileNumber)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(newDeviceRequestPassword))
		{
			CryptoSession.getCurrent().setInitializationVector(this.initializationVector);
			this.yonaPassword = new StringFieldEncryptor().convertToEntityAttribute(this.yonaPasswordCipherText);
			this.decryptionCheck = new StringFieldEncryptor().convertToEntityAttribute(this.decryptionCheckCipherText);
		}

		if (!this.isDecryptedProperly())
		{
			throw DeviceRequestException.invalidNewDeviceRequestPassword(mobileNumber);
		}
	}
}
