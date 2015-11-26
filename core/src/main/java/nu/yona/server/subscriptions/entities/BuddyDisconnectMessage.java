package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@Entity
public class BuddyDisconnectMessage extends BuddyMessage
{
	private boolean isProcessed;
	private DropBuddyReason reason;

	public BuddyDisconnectMessage(UUID id, UUID userID, UUID loginID, String nickname, String message, DropBuddyReason reason)
	{
		super(id, loginID, userID, nickname, message);
		this.reason = reason;
	}

	// Default constructor is required for JPA
	public BuddyDisconnectMessage()
	{
		super();
	}

	public static BuddyDisconnectMessage createInstance(UUID userID, UUID loginID, String nickname, String message,
			DropBuddyReason reason)
	{
		return new BuddyDisconnectMessage(UUID.randomUUID(), userID, loginID, nickname, message, reason);
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
