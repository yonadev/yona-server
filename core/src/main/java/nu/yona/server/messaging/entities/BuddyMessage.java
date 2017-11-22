/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;

@Entity
public abstract class BuddyMessage extends Message
{
	@Transient
	private UUID senderUserId;
	private byte[] senderUserIdCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private String senderNickname;
	private byte[] senderNicknameCiphertext;

	@Transient
	private Optional<UUID> senderUserPhotoId;
	private byte[] senderUserPhotoIdCiphertext;

	// Default constructor is required for JPA
	protected BuddyMessage()
	{
		super(null);
	}

	protected BuddyMessage(BuddyInfoParameters buddyInfoParameters, String message)
	{
		this(buddyInfoParameters, false, message);
	}

	protected BuddyMessage(BuddyInfoParameters buddyInfoParameters, boolean isSentItem, String message)
	{
		super(buddyInfoParameters.relatedUserAnonymizedId, isSentItem);
		this.senderUserId = buddyInfoParameters.userId;
		this.senderNickname = buddyInfoParameters.nickname;
		this.senderUserPhotoId = buddyInfoParameters.userPhotoId;
		this.message = message;
	}

	/**
	 * Returns the user sending this message.
	 * 
	 * @return The user sending this message. Might be null if that user is already deleted.
	 */
	public Optional<User> getSenderUser()
	{
		return (senderUserId == null) ? Optional.empty() : Optional.ofNullable(User.getRepository().findOne(senderUserId));
	}

	/**
	 * Returns the ID of the user sending this message.
	 * 
	 * @return The ID of the user sending this message. Might be null if that user was already deleted at the time this message
	 *         was sent on behalf of that user.
	 */
	public UUID getSenderUserId()
	{
		return senderUserId;
	}

	public String getMessage()
	{
		return message;
	}

	public String getSenderNickname()
	{
		return senderNickname;
	}

	public Optional<UUID> getSenderUserPhotoId()
	{
		return senderUserPhotoId;
	}

	@Override
	public void encrypt()
	{
		senderUserIdCiphertext = SecretKeyUtil.encryptUuid(senderUserId);
		messageCiphertext = SecretKeyUtil.encryptString(message);
		senderNicknameCiphertext = SecretKeyUtil.encryptString(senderNickname);
		senderUserPhotoIdCiphertext = SecretKeyUtil.encryptUuid(senderUserPhotoId.orElse(null));
	}

	@Override
	public void decrypt()
	{
		senderUserId = SecretKeyUtil.decryptUuid(senderUserIdCiphertext);
		message = SecretKeyUtil.decryptString(messageCiphertext);
		senderNickname = SecretKeyUtil.decryptString(senderNicknameCiphertext);
		senderUserPhotoId = Optional.ofNullable(SecretKeyUtil.decryptUuid(senderUserPhotoIdCiphertext));
	}

	/*
	 * Structure to pass some information about the buddy that sent the message.
	 */
	public static class BuddyInfoParameters
	{
		public final UUID relatedUserAnonymizedId;

		public final UUID userId;
		public final String nickname;
		public final Optional<UUID> userPhotoId;

		public BuddyInfoParameters(UUID relatedUserAnonymizedId, UUID userId, String nickname, Optional<UUID> userPhotoId)
		{
			this.relatedUserAnonymizedId = relatedUserAnonymizedId;

			this.userId = userId;
			this.nickname = nickname;
			this.userPhotoId = userPhotoId;
		}

		public static BuddyInfoParameters createInstance(User userEntity)
		{
			return new BuddyInfoParameters(userEntity.getUserAnonymizedId(), userEntity.getId(), userEntity.getNickname(),
					userEntity.getUserPhotoId());
		}

		public static BuddyInfoParameters createInstance(User userEntity, String nickname)
		{
			return new BuddyInfoParameters(userEntity.getUserAnonymizedId(), userEntity.getId(), nickname,
					userEntity.getUserPhotoId());
		}

		public static BuddyInfoParameters createInstance(Buddy buddy, UUID buddyUserAnonymizedId)
		{
			return new BuddyInfoParameters(buddyUserAnonymizedId, null, buddy.getNickname(), buddy.getUserPhotoId());
		}
	}
}
