package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@Entity
public class BuddyConnectRemoveMessage extends BuddyMessage
{
	private boolean isProcessed;
	private DropBuddyReason reason;

	public BuddyConnectRemoveMessage(UUID id, UUID userID, UUID loginID, String nickname, String message, DropBuddyReason reason)
	{
		super(id, loginID, userID, nickname, message);
		this.reason = reason;
	}

	// Default constructor is required for JPA
	public BuddyConnectRemoveMessage()
	{
		super();
	}

	public static BuddyConnectRemoveMessage createInstance(UUID userID, UUID loginID, String nickname, String message,
			DropBuddyReason reason)
	{
		return new BuddyConnectRemoveMessage(UUID.randomUUID(), userID, loginID, nickname, message, reason);
	}

	public DropBuddyReason getReason()
	{
		return reason;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	public void setProcessed()
	{
		this.isProcessed = true;
	}
}
