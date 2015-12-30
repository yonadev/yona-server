package nu.yona.server.subscriptions.service;

import nu.yona.server.exceptions.YonaException;

public class BuddyServiceException extends YonaException
{
	private static final long serialVersionUID = 3301297701692886481L;

	protected BuddyServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	protected BuddyServiceException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static BuddyServiceException userCannotBeNull()
	{
		return new BuddyServiceException("error.buddy.user.cannot.be.null");
	}

	public static BuddyServiceException actionUnknown(String action)
	{
		return new BuddyServiceException("error.buddy.action.unknown", action);
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
}
