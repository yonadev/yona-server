package nu.yona.server.messaging.service;

import java.util.UUID;

import nu.yona.server.messaging.entities.MessageDestination;

public class MessageDestinationDTO
{
	private UUID id;

	public MessageDestinationDTO(UUID id)
	{
		if (id == null)
		{
			throw new IllegalArgumentException("id cannot be null");
		}

		this.id = id;
	}

	public static MessageDestinationDTO createInstance(MessageDestination entity)
	{
		return new MessageDestinationDTO(entity.getID());
	}

	public UUID getID()
	{
		return id;
	}
}
