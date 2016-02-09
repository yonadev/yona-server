package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.subscriptions.entities.BuddyMessage;

@Entity
public class DiscloseResponseMessage extends BuddyMessage
{
	private UUID targetGoalConflictMessageID;
	private Status status;

	// Default constructor is required for JPA
	public DiscloseResponseMessage()
	{

	}

	protected DiscloseResponseMessage(UUID id, UUID respondingUserID, UUID relatedUserAnonymizedID,
			UUID targetGoalConflictMessageID, Status status, String nickname, String message)
	{
		super(id, relatedUserAnonymizedID, respondingUserID, nickname, message);
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

	public static DiscloseResponseMessage createInstance(UUID respondingUserID, UUID relatedUserAnonymizedID,
			UUID targetGoalConflictMessageID, Status status, String nickname, String message)
	{
		return new DiscloseResponseMessage(UUID.randomUUID(), respondingUserID, relatedUserAnonymizedID,
				targetGoalConflictMessageID, status, nickname, message);
	}
}
