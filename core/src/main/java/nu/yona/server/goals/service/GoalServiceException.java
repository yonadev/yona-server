package nu.yona.server.goals.service;

import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class GoalServiceException extends YonaException
{
	private static final long serialVersionUID = -3190676140223118243L;

	private GoalServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static GoalServiceException goalNotFoundById(UUID userID, UUID goalID)
	{
		return new GoalServiceException("error.goal.not.found.id", userID, goalID);
	}

	public static GoalServiceException cannotAddSecondGoalOnActivityCategory(String activityCategoryName)
	{
		return new GoalServiceException("error.goal.cannot.add.second.on.activity.category", activityCategoryName);
	}
}
