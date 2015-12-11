package nu.yona.server.messaging.service;

import java.security.PublicKey;
import java.util.UUID;

import nu.yona.server.messaging.entities.MessageDestination;

public class MessageDestinationDTO
{
	private UUID id;
	private PublicKey publicKey;

	private MessageDestinationDTO(UUID id, PublicKey publicKey)
	{
		if (id == null)
		{
			throw new IllegalArgumentException("id cannot be null");
		}

		this.id = id;
		this.publicKey = publicKey;
	}

	public static MessageDestinationDTO createInstance(MessageDestination entity)
	{
		return new MessageDestinationDTO(entity.getID(), entity.getPublicKey());
	}

	public UUID getID()
	{
		return id;
	}

	public PublicKey getPublicKey()
	{
		return publicKey;
	}
}
