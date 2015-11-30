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
	private boolean isProcessed;

	// Default constructor is required for JPA
	public DiscloseResponseMessage()
	{
		super(null, null, null, null, null);
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

	@Override
	public boolean canBeDeleted()
	{
		return isProcessed;
	}

	public static DiscloseResponseMessage createInstance(UUID respondingUserID, UUID relatedUserAnonymizedID,
			UUID targetGoalConflictMessageID, Status status, String nickname, String message)
	{
		return new DiscloseResponseMessage(UUID.randomUUID(), respondingUserID, relatedUserAnonymizedID,
				targetGoalConflictMessageID, status, nickname, message);
	}
}
