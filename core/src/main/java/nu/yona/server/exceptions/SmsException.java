package nu.yona.server.exceptions;

/**
 * This exception is to be used when email sending fails.
 */
public class SmsException extends YonaException
{
	private static final long serialVersionUID = 1341422955591660135L;

	private SmsException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private SmsException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static SmsException smsSendingFailed(Exception cause)
	{
		return new SmsException(cause, "error.sms.sending.failed.exception", cause.getMessage());
	}

	public static SmsException smsSendingFailed(int httpResponseCode, String httpResponseBody)
	{
		return new SmsException("error.sms.sending.failed.httpStatus", httpResponseBody, httpResponseBody);
	}
}
