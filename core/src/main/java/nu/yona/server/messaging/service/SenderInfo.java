package nu.yona.server.messaging.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Sender info of the message. The class contents can only be determined when the user reads incoming messages, because buddy IDs
 * are only known to the user, and for the analysis service sending goal conflicts the user ID and nickname are not known.
 */
public final class SenderInfo
{
	private final Optional<UserDTO> user;
	private final String nickname;
	private final boolean isBuddy;
	private final Optional<BuddyDTO> buddy;

	private SenderInfo(Optional<UserDTO> user, String nickname, boolean isBuddy, Optional<BuddyDTO> buddy)
	{
		this.user = user;
		this.nickname = nickname;
		this.isBuddy = isBuddy;
		this.buddy = buddy;
	}

	public String getNickname()
	{
		return nickname;
	}

	public boolean isBuddy()
	{
		return isBuddy;
	}

	public Optional<UserDTO> getUser()
	{
		return user;
	}

	public Optional<BuddyDTO> getBuddy()
	{
		return buddy;
	}

	@Component
	public static class Factory
	{
		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserService userService;

		@Autowired
		private Translator translator;

		public SenderInfo createInstanceForBuddy(UUID userID, String nickname, UUID buddyID)
		{
			return new SenderInfo(Optional.of(userService.getPublicUser(userID)), nickname, true,
					Optional.of(buddyService.getBuddy(buddyID)));
		}

		public SenderInfo createInstanceForDetachedBuddy(Optional<UserDTO> user, String nickname)
		{
			return new SenderInfo(user, nickname, true, Optional.empty());
		}

		public SenderInfo createInstanceForSelf(UUID userID, String nickname)
		{
			String selfNickname = translator.getLocalizedMessage("message.self.nickname", nickname);
			return new SenderInfo(Optional.of(userService.getPrivateUser(userID)), selfNickname, false, Optional.empty());
		}
	}
}