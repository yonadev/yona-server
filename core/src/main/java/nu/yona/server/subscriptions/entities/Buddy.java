/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.crypto.UUIDFieldEncrypter;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

@Entity
@Table(name = "BUDDIES")
public class Buddy extends EntityWithID
{
	public static BuddyRepository getRepository()
	{
		return (BuddyRepository) RepositoryProvider.getRepository(Buddy.class, UUID.class);
	}

	@Column(nullable = true)
	private int touchVersion;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID userID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID buddyAnonymizedID;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickName;

	// Default constructor is required for JPA
	public Buddy()
	{
		super(null);
	}

	private Buddy(UUID id, UUID userID, String nickName, UUID buddyAnonymizedID)
	{
		super(id);
		this.userID = userID;
		this.nickName = nickName;
		this.buddyAnonymizedID = buddyAnonymizedID;
	}

	public static Buddy createInstance(UUID buddyUserID, String nickName, Status sendingStatus, Status receivingStatus)
	{
		BuddyAnonymized buddyAnonymized = BuddyAnonymized.createInstance(sendingStatus, receivingStatus);
		buddyAnonymized = BuddyAnonymized.getRepository().save(buddyAnonymized);
		return new Buddy(UUID.randomUUID(), buddyUserID, nickName, buddyAnonymized.getID());
	}

	public UUID getBuddyAnonymizedID()
	{
		return buddyAnonymizedID;
	}

	BuddyAnonymized getBuddyAnonymized()
	{
		return BuddyAnonymized.getRepository().findOne(buddyAnonymizedID);
	}

	public String getNickName()
	{
		return nickName;
	}

	public void setNickName(String nickName)
	{
		this.nickName = nickName;
	}

	public UUID getUserID()
	{
		return userID;
	}

	public User getUser()
	{
		return User.getRepository().findOne(userID);
	}

	public UUID getVPNLoginID()
	{
		return getBuddyAnonymized().getVPNLoginID();
	}

	public Status getReceivingStatus()
	{
		return getBuddyAnonymized().getReceivingStatus();
	}

	public void setReceivingStatus(Status status)
	{
		getBuddyAnonymized().setReceivingStatus(status);
	}

	public void setSendingStatus(Status status)
	{
		getBuddyAnonymized().setSendingStatus(status);
	}

	public Status getSendingStatus()
	{
		return getBuddyAnonymized().getSendingStatus();
	}

	public void setVPNLoginID(UUID vpnLoginID)
	{
		BuddyAnonymized buddyAnonymized = getBuddyAnonymized();
		buddyAnonymized.setVPNLoginID(vpnLoginID);
		BuddyAnonymized.getRepository().save(buddyAnonymized);
	}

	public Buddy touch()
	{
		touchVersion++;
		return this;
	}
}
