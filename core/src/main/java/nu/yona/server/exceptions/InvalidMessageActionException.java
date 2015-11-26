package nu.yona.server.exceptions;

/**
 * This exception is to be used when an action is called for a message which is not supported.
 */
public class InvalidMessageActionException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private InvalidMessageActionException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private InvalidMessageActionException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static InvalidMessageActionException unprocessedMessageCannotBeDeleted()
	{
		return new InvalidMessageActionException("error.cannot.delete.unprocessed.message");
	}
}
