package nu.yona.server.subscriptions.service;

import nu.yona.server.exceptions.YonaException;

public class LDAPUserServiceException extends YonaException
{
	private static final long serialVersionUID = 6076507810167855352L;

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

	public static LDAPUserServiceException emptyVpnLoginId()
	{
		return new LDAPUserServiceException("error.ldapuser.empty.vpn.login.id");
	}

	public static LDAPUserServiceException emptyVpnPassword()
	{
		return new LDAPUserServiceException("error.ldapuser.empty.vpn.password");
	}
}
