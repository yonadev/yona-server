/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;

import nu.yona.server.exceptions.YonaException;

public class BuddyServiceException extends YonaException
{
	private static final long serialVersionUID = 3301297701692886481L;

	protected BuddyServiceException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	protected BuddyServiceException(Throwable t, String messageId, Serializable... parameters)
	{
		super(t, messageId, parameters);
	}

	public static BuddyServiceException messageEntityCannotBeNull()
	{
		return new BuddyServiceException("error.buddy.message.entity.cannot.be.null");
	}

	public static BuddyServiceException userAnonymizedIdCannotBeNull()
	{
		return new BuddyServiceException("error.buddy.useranonymizedid.cannot.be.null");
	}

	public static BuddyServiceException requestingUserCannotBeNull()
	{
		return new BuddyServiceException("error.buddy.requesting.user.cannot.be.null");
	}

	public static BuddyServiceException onlyTwoWayBuddiesAllowed()
	{
		return new BuddyServiceException("error.buddy.only.twoway.buddies.allowed");
	}

	public static BuddyServiceException requestingUserBuddyIsNull()
	{
		return new BuddyServiceException("error.buddy.requesting.user.buddy.is.null");
	}

	public static BuddyServiceException acceptingUserIsNull()
	{
		return new BuddyServiceException("error.buddy.accepting.user.is.null");
	}

	public static BuddyServiceException missingUser(String rel)
	{
		return new BuddyServiceException("error.buddy.request.must.contain.user.inside.embedded", rel);
	}

	public static BuddyServiceException cannotInviteSelf()
	{
		return new BuddyServiceException("error.buddy.cannot.invite.self");
	}

	public static BuddyServiceException cannotInviteExistingBuddy()
	{
		return new BuddyServiceException("error.buddy.cannot.invite.existing.buddy");
	}
}
