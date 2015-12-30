package nu.yona.server.exceptions;

/**
 * This exception is to be used when email sending fails.
 */
public class EmailException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private EmailException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private EmailException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static EmailException emailSendingFailed(Exception cause)
	{
		return new EmailException(cause, "error.email.sending.failed", cause.getMessage());
	}
}
