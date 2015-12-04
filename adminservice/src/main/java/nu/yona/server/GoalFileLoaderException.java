package nu.yona.server;

import nu.yona.server.exceptions.YonaException;

public class GoalFileLoaderException extends YonaException
{
	private static final long serialVersionUID = -8542854561248198195L;

	public GoalFileLoaderException(Throwable cause, String messageId, Object... parameters)
	{
		super(cause, messageId, parameters);
	}

	public static GoalFileLoaderException loadingGoalsFromFile(Throwable cause, String filename)
	{
		return new GoalFileLoaderException(cause, "error.loading.goals.from.file", filename);
	}
}
