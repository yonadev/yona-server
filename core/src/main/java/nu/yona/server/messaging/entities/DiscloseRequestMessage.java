package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
public class DiscloseRequestMessage extends Message
{
	private UUID targetGoalConflictMessageID;
	private Status status;
	private String nickname;

	// Default constructor is required for JPA
	public DiscloseRequestMessage()
	{
		super(null, null);
	}

	protected DiscloseRequestMessage(UUID id, UUID requestingUserVPNLoginID, UUID targetGoalConflictMessageID,
			String nickname)
	{
		super(id, requestingUserVPNLoginID);
		this.targetGoalConflictMessageID = targetGoalConflictMessageID;
		this.status = Status.DISCLOSE_REQUESTED;
		this.nickname = nickname;
	}

	public UUID getTargetGoalConflictMessageID()
	{
		return targetGoalConflictMessageID;
	}

	public GoalConflictMessage getTargetGoalConflictMessage()
	{
		return (GoalConflictMessage) GoalConflictMessage.getRepository().findOne(targetGoalConflictMessageID);
	}

	public String getNickname()
	{
		return nickname;
	}

	public Status getStatus()
	{
		return status;
	}

	public UserAnonymized getUser()
	{
		return UserAnonymized.getRepository().findOne(getRelatedVPNLoginID());
	}

	public boolean isAccepted()
	{
		return status == Status.DISCLOSE_ACCEPTED;
	}

	public boolean isRejected()
	{
		return status == Status.DISCLOSE_REJECTED;
	}

	public void setStatus(Status status)
	{
		this.status = status;
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
		return this.status == Status.DISCLOSE_ACCEPTED || this.status == Status.DISCLOSE_REJECTED;
	}

	public static Message createInstance(UUID requestingUserVPNLoginID, String requestingUserNickname,
			GoalConflictMessage targetGoalConflictMessage)
	{
		return new DiscloseRequestMessage(UUID.randomUUID(), requestingUserVPNLoginID,
				targetGoalConflictMessage.getID(), requestingUserNickname);
	}
}
