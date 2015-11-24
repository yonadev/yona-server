package nu.yona.server;

import nu.yona.server.exceptions.YonaException;

public class GoalFileLoaderException extends YonaException
{
	public GoalFileLoaderException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static GoalFileLoaderException loadingGoalsFromFile(String filename)
	{
		return new GoalFileLoaderException("error.loading.goals.from.file", filename);
	}
}
