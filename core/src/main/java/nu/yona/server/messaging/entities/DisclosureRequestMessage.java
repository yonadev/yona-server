/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;

@Entity
public class DisclosureRequestMessage extends BuddyMessage
{
	private long targetGoalConflictMessageId;
	private Status status;

	// Default constructor is required for JPA
	public DisclosureRequestMessage()
	{
	}

	private DisclosureRequestMessage(UUID senderUserId, UUID senderUserAnonymizedId, long targetGoalConflictMessageId,
			String senderUserNickname, String message)
	{
		super(senderUserId, senderUserAnonymizedId, senderUserNickname, message);
		this.targetGoalConflictMessageId = targetGoalConflictMessageId;
		this.status = Status.DISCLOSURE_REQUESTED;
	}

	public long getTargetGoalConflictMessageId()
	{
		return targetGoalConflictMessageId;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return (GoalConflictMessage) GoalConflictMessage.getRepository().findOne(targetGoalConflictMessageId);
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		super.encrypt(encryptor);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		super.decrypt(decryptor);
	}

	public static Message createInstance(UUID senderUserId, UUID senderUserAnonymizedId, String senderUserNickname,
			String message, GoalConflictMessage targetGoalConflictMessage)
	{
		return new DisclosureRequestMessage(senderUserId, senderUserAnonymizedId, targetGoalConflictMessage.getId(),
				senderUserNickname, message);
	}
}
