/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.service.DeviceRequestException;

/*
 * A request to add another device for an existing user. The data cannot be encrypted with the user 'password' or auto-generated
 * key because that is stored on the device and cannot be entered by the user. Therefore we have to transfer the user 'password'
 * to the new device and obviously encrypt it (with a secret entered by the user, e.g. her PIN or a one time password) while
 * transferring.
 */
@Entity
@Table(name = "NEW_DEVICE_REQUESTS")
public class NewDeviceRequest extends EntityWithID
{
	public static NewDeviceRequestRepository getRepository()
	{
		return (NewDeviceRequestRepository) RepositoryProvider.getRepository(NewDeviceRequest.class, UUID.class);
	}

	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Transient
	private String decryptionCheck;
	private String decryptionCheckCipherText;

	private Date creationDateTime;

	@Transient
	private String userPassword;
	private String userPasswordCipherText;

	private byte[] initializationVector;

	public Date getCreationTime()
	{
		return creationDateTime;
	}

	public String getUserPassword()
	{
		return userPassword;
	}

	// Default constructor is required for JPA
	public NewDeviceRequest()
	{
		super(null);
	}

	private NewDeviceRequest(UUID id, String userPassword, Date creationDateTime)
	{
		super(id);
		this.userPassword = userPassword;
		this.creationDateTime = creationDateTime;
		this.decryptionCheck = buildDecryptionCheck();
	}

	public static NewDeviceRequest createInstance(String userPassword)
	{
		return new NewDeviceRequest(UUID.randomUUID(), userPassword, new Date());
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

	public void encryptUserPassword(String userSecret)
	{
		CryptoSession.execute(Optional.of(userSecret), null, () -> {
			this.initializationVector = CryptoSession.getCurrent().generateInitializationVector();
			CryptoSession.getCurrent().setInitializationVector(this.initializationVector);
			this.userPasswordCipherText = new StringFieldEncrypter().convertToDatabaseColumn(this.userPassword);
			this.decryptionCheckCipherText = new StringFieldEncrypter().convertToDatabaseColumn(this.decryptionCheck);
			return null;
		});
	}

	public void decryptUserPassword(String userSecret, String mobileNumber)
	{
		CryptoSession.execute(Optional.of(userSecret), null, () -> {
			CryptoSession.getCurrent().setInitializationVector(this.initializationVector);
			this.userPassword = new StringFieldEncrypter().convertToEntityAttribute(this.userPasswordCipherText);
			this.decryptionCheck = new StringFieldEncrypter().convertToEntityAttribute(this.decryptionCheckCipherText);
			return null;
		});

		if (!this.isDecryptedProperly())
		{
			throw DeviceRequestException.invalidSecret(mobileNumber);
		}
	}
}
