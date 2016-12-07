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
public class DisclosureResponseMessage extends BuddyMessage
{
	private long targetGoalConflictMessageID;
	private Status status;

	// Default constructor is required for JPA
	public DisclosureResponseMessage()
	{

	}

	private DisclosureResponseMessage(UUID senderUserID, UUID senderUserAnonymizedID, long targetGoalConflictMessageID,
			Status status, String senderNickname, String message)
	{
		super(senderUserID, senderUserAnonymizedID, senderNickname, message);
		this.targetGoalConflictMessageID = targetGoalConflictMessageID;
		this.status = status;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return (GoalConflictMessage) GoalConflictMessage.getRepository().findOne(targetGoalConflictMessageID);
	}

	public Status getStatus()
	{
		return status;
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

	public static DisclosureResponseMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID,
			long targetGoalConflictMessageID, Status status, String senderNickname, String message)
	{
		return new DisclosureResponseMessage(senderUserID, senderUserAnonymizedID, targetGoalConflictMessageID, status,
				senderNickname, message);
	}
}
