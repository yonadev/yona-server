package nu.yona.server.goals.service;

import nu.yona.server.exceptions.YonaException;

public class GoalServiceException extends YonaException
{
	private static final long serialVersionUID = 3674627870453389914L;

	private GoalServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private GoalServiceException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static GoalServiceException actionNotSupported(String action)
	{
		return new GoalServiceException("error.goal.action.not.supported", action);
	}

	public static GoalServiceException originCannotBeNull()
	{
		return new GoalServiceException("error.goal.origin.cannot.be.null");
	}
}
