/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public abstract class BuddyConnectionChangeMessage extends BuddyMessage
{
	@Transient
	private String firstName;
	private byte[] firstNameCiphertext;

	@Transient
	private String lastName;
	private byte[] lastNameCiphertext;

	// Default constructor is required for JPA
	protected BuddyConnectionChangeMessage()
	{
		super();
	}

	public BuddyConnectionChangeMessage(BuddyInfoParameters buddyInfoParameters, String message)
	{
		super(buddyInfoParameters, message);
		this.firstName = buddyInfoParameters.firstName;
		this.lastName = buddyInfoParameters.lastName;
	}

	@Override
	public void encrypt()
	{
		super.encrypt();
		firstNameCiphertext = SecretKeyUtil.encryptString(firstName);
		lastNameCiphertext = SecretKeyUtil.encryptString(lastName);
	}

	@Override
	public void decrypt()
	{
		super.decrypt();
		firstName = SecretKeyUtil.decryptString(firstNameCiphertext);
		lastName = SecretKeyUtil.decryptString(lastNameCiphertext);
	}

	public String getFirstName()
	{
		return (firstName == null) ? getTranslation("message.alternative.first.name", getSenderNickname()) : firstName;
	}

	public String getLastName()
	{
		return (lastName == null) ? getTranslation("message.alternative.last.name", getSenderNickname()) : lastName;
	}

	private String getTranslation(String messageId, String nickname)
	{
		return Translator.getInstance().getLocalizedMessage(messageId, nickname);
	}
}
