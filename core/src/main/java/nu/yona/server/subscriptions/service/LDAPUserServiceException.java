/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;

import nu.yona.server.exceptions.YonaException;

public class LDAPUserServiceException extends YonaException
{
	private static final long serialVersionUID = 6076507810167855352L;

	protected LDAPUserServiceException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	protected LDAPUserServiceException(Throwable t, String messageId, Serializable... parameters)
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
