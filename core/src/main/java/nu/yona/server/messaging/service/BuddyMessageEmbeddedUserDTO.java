package nu.yona.server.messaging.service;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.subscriptions.service.UserDTO;

public abstract class BuddyMessageEmbeddedUserDTO extends BuddyMessageDTO
{
	private Map<String, Object> embeddedResources = Collections.emptyMap();

	protected BuddyMessageEmbeddedUserDTO(UUID id, Date creationTime, UserDTO user, String nickname, String message)
	{
		super(id, creationTime, user, nickname, message);
	}

	protected BuddyMessageEmbeddedUserDTO(UUID id, Date creationTime, UUID targetGoalConflictMessageOriginID, UserDTO user,
			String nickname, String message)
	{
		super(id, creationTime, user, nickname, message);
	}

	@JsonProperty("_embedded")
	@JsonInclude(Include.NON_EMPTY)
	public Map<String, Object> getEmbeddedResources()
	{
		return embeddedResources;
	}

	public void setEmbeddedUser(String rel, Object userResource)
	{
		embeddedResources = Collections.singletonMap(rel, userResource);
	}
}
