package nu.yona.server.subscriptions.service;

import nu.yona.server.exceptions.YonaException;

public class LDAPUserServiceException extends YonaException
{
	protected LDAPUserServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	protected LDAPUserServiceException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static LDAPUserServiceException actionUnknown(String action)
	{
		return new LDAPUserServiceException("error.ldapuser.action.unknown", action);
	}
}
