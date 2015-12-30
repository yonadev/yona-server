package nu.yona.server.exceptions;

/**
 * This exception is to be used in case the mobile number confirmation code is wrong.
 */
public class MobileNumberConfirmationException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private MobileNumberConfirmationException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private MobileNumberConfirmationException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static MobileNumberConfirmationException confirmationCodeMismatch(String mobileNumber, String code)
	{
		return new MobileNumberConfirmationException("error.mobile.number.confirmation.code.mismatch", mobileNumber, code);
	}

	public static MobileNumberConfirmationException confirmationCodeNotSet(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.confirmation.code.not.set", mobileNumber);
	}

	public static MobileNumberConfirmationException mobileNumberAlreadyConfirmed(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.already.confirmed", mobileNumber);
	}

	public static MobileNumberConfirmationException notConfirmed(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.not.confirmed", mobileNumber);
	}
}
