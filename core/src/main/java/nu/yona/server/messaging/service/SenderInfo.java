package nu.yona.server.messaging.service;

import java.util.Optional;
import java.util.UUID;

import nu.yona.server.subscriptions.service.UserDTO;

/*
 * Sender info of the message. The class contents can only be determined when the user reads incoming messages, because buddy IDs
 * are only known to the user, and for the analysis service sending goal conflicts the user ID and nickname are not known.
 */
public final class SenderInfo
{
	private final UUID userID;
	private final String nickname;
	private final boolean isBuddy;
	private final Optional<UUID> buddyID;

	private SenderInfo(UUID userID, String nickname, boolean isBuddy, Optional<UUID> buddyID)
	{
		this.userID = userID;
		this.nickname = nickname;
		this.isBuddy = isBuddy;
		this.buddyID = buddyID;
	}

	public UUID getUserID()
	{
		return userID;
	}

	public String getNickname()
	{
		return nickname;
	}

	public boolean isBuddy()
	{
		return isBuddy;
	}

	public Optional<UUID> getBuddyID()
	{
		return buddyID;
	}

	public static SenderInfo createInstanceBuddy(UUID userID, String nickname, UUID buddyID)
	{
		return new SenderInfo(userID, nickname, true, Optional.of(buddyID));
	}

	public static SenderInfo createInstanceBuddyDetached(UUID userID, String nickname)
	{
		return new SenderInfo(userID, nickname, true, Optional.empty());
	}

	public static SenderInfo createInstanceBuddyDetached(UserDTO user, String nickname)
	{
		// user may be null if removed
		return createInstanceBuddyDetached(user != null ? user.getID() : null, nickname);
	}

	public static SenderInfo createInstanceSelf(UUID userID, String nickname)
	{
		return new SenderInfo(userID, "<self>", false, Optional.empty());
	}
}