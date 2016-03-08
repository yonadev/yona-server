package nu.yona.server.messaging.service;

import java.util.Date;
import java.util.UUID;

import nu.yona.server.subscriptions.service.UserDTO;

public abstract class BuddyMessageLinkedUserDTO extends BuddyMessageDTO
{

	protected BuddyMessageLinkedUserDTO(UUID id, Date creationTime, UserDTO user, String nickname, String message)
	{
		super(id, creationTime, user, nickname, message);
	}

	protected BuddyMessageLinkedUserDTO(UUID id, Date creationTime, UUID relatedMessageID, UserDTO user, String nickname,
			String message)
	{
		super(id, creationTime, relatedMessageID, user, nickname, message);
	}
}
