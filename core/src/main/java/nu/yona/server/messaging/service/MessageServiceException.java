package nu.yona.server.messaging.service;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.Message;

public class MessageServiceException extends YonaException
{

	private MessageServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private MessageServiceException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static MessageServiceException noDtoManagerRegistered(Class<? extends Message> clz)
	{
		return new MessageServiceException("error.message.no.dto.manager.registered", clz);
	}

	public static MessageServiceException actionNotSupported(String action)
	{
		return new MessageServiceException("error.message.action.not.supported", action);
	}

	public static MessageServiceException anonymizedUserIdMustBeSet()
	{
		return new MessageServiceException("error.message.anonymized.user.id.must.be.set");
	}
}
