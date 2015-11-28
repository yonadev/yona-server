package nu.yona.server;

import nu.yona.server.exceptions.YonaException;

public class GoalFileLoaderException extends YonaException
{
	private static final long serialVersionUID = -8542854561248198195L;

	public GoalFileLoaderException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static GoalFileLoaderException loadingGoalsFromFile(String filename)
	{
		return new GoalFileLoaderException("error.loading.goals.from.file", filename);
	}
}
