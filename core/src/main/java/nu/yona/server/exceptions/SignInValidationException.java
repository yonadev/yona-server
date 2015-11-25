package nu.yona.server.exceptions;

/**
 * This exception is to be used in case user sign-in code is wrong.
 */
public class SignInValidationException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private SignInValidationException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private SignInValidationException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static SignInValidationException signInCodeMismatch()
	{
		return new SignInValidationException("error.sign-in.code.mismatch");
	}

	public static SignInValidationException signInCodeNotSet()
	{
		return new SignInValidationException("error.sign-in.code.not.set");
	}

	public static SignInValidationException userCannotBeActivated()
	{
		return new SignInValidationException("error.sign-in.user.cannot.be.activated");
	}
}
