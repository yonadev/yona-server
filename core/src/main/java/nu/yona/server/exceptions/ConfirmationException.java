package nu.yona.server.exceptions;

public class ConfirmationException extends YonaException
{
	private static final long serialVersionUID = -2153058082705686936L;

	private final int remainingAttempts;

	public ConfirmationException(String messageId, Object... parameters)
	{
		this(-1, messageId, parameters);
	}

	public ConfirmationException(Throwable throwable, String messageId, Object... parameters)
	{
		super(throwable, messageId, parameters);
		this.remainingAttempts = -1;
	}

	public ConfirmationException(int remainingAttempts, String messageId, Object... parameters)
	{
		super(messageId, parameters);
		this.remainingAttempts = remainingAttempts;
	}

	public int getRemainingAttempts()
	{
		return remainingAttempts;
	}

}