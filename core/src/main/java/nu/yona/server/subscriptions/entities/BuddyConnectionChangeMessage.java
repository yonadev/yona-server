/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.Message;

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

	/**
	 * Copy constructor. See {@link nu.yona.server.messaging.entities.Message#duplicate()}
	 * 
	 * @param original Message to copy.
	 */
	public BuddyConnectionChangeMessage(BuddyConnectionChangeMessage original)
	{
		super(original);
		this.firstName = original.firstName;
		this.lastName = original.lastName;
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

	public abstract boolean isUserFetchable();

	public String getFirstName()
	{
		return firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	/**
	 * Determines the first name (see {@link Buddy#determineName(Supplier, Optional, Function, String, String)}).
	 * 
	 * @param user Optional user
	 * @return The first name or a substitute for it (never null)
	 */
	public String determineFirstName(Optional<User> senderUser)
	{
		return Buddy.determineName(this::getFirstName, senderUser, User::getFirstName, "message.alternative.first.name",
				getSenderNickname());
	}

	/**
	 * Determines the last name (see {@link Buddy#determineName(Supplier, Optional, Function, String, String)}).
	 * 
	 * @param user Optional user
	 * @return The last name or a substitute for it (never null)
	 */
	public String determineLastName(Optional<User> senderUser)
	{
		return Buddy.determineName(this::getLastName, senderUser, User::getLastName, "message.alternative.last.name",
				getSenderNickname());
	}
}
