package nu.yona.server.exceptions;

/**
 * This exception is to be used in case user overwrite confirmation code is wrong.
 */
public class UserOverwriteConfirmationException extends YonaException
{
	private static final long serialVersionUID = -3653199898915579250L;

	private UserOverwriteConfirmationException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private UserOverwriteConfirmationException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static UserOverwriteConfirmationException confirmationCodeMismatch(String mobileNumber, String code)
	{
		return new UserOverwriteConfirmationException("error.user.overwrite.confirmation.code.mismatch", mobileNumber, code);
	}

	public static UserOverwriteConfirmationException confirmationCodeNotSet(String mobileNumber)
	{
		return new UserOverwriteConfirmationException("error.user.overwrite.confirmation.code.not.set", mobileNumber);
	}
}
