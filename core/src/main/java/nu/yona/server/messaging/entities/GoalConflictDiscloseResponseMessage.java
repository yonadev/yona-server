package nu.yona.server.messaging.entities;

import java.util.UUID;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;

public class GoalConflictDiscloseResponseMessage extends Message
{
	private UUID targetGoalConflictMessageID;
	private Status status;
	private String nickname;
	private boolean isProcessed;

	// Default constructor is required for JPA
	public GoalConflictDiscloseResponseMessage()
	{
		super(null, null);
	}

	protected GoalConflictDiscloseResponseMessage(UUID id, UUID relatedUserAnonymizedID, UUID targetGoalConflictMessageID,
			Status status, String nickname)
	{
		super(id, relatedUserAnonymizedID);
		this.targetGoalConflictMessageID = targetGoalConflictMessageID;
		this.status = status;
		this.nickname = nickname;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return (GoalConflictMessage) GoalConflictMessage.getRepository().findOne(targetGoalConflictMessageID);
	}

	public Status getStatus()
	{
		return status;
	}

	public String getNickname()
	{
		return nickname;
	}

	@Override
	protected void encrypt(Encryptor encryptor)
	{

	}

	@Override
	protected void decrypt(Decryptor decryptor)
	{

	}

	@Override
	public boolean canBeDeleted()
	{
		return isProcessed;
	}

	public static GoalConflictDiscloseResponseMessage createInstance(UUID relatedUserAnonymizedID,
			UUID targetGoalConflictMessageID, Status status, String nickname)
	{
		return new GoalConflictDiscloseResponseMessage(UUID.randomUUID(), relatedUserAnonymizedID, targetGoalConflictMessageID,
				status, nickname);
	}
}
