package nu.yona.server;

import java.net.URI;

import nu.yona.server.exceptions.YonaException;

public class BruteForceException extends YonaException
{
	private static final long serialVersionUID = 2837321499539339643L;

	protected BruteForceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static BruteForceException tooManyAttempts(URI uri)
	{
		return new BruteForceException("error.too.many.wrong.attempts", uri);
	}
}
