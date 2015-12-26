package nu.yona.server.dbinit;

import nu.yona.server.exceptions.YonaException;

public class ActivityCategoryFileLoaderException extends YonaException
{
	private static final long serialVersionUID = -8542854561248198195L;

	public ActivityCategoryFileLoaderException(Throwable cause, String messageId, Object... parameters)
	{
		super(cause, messageId, parameters);
	}

	public static ActivityCategoryFileLoaderException loadingActivityCategoriesFromFile(Throwable cause, String filename)
	{
		return new ActivityCategoryFileLoaderException(cause, "error.loading.activitycategories.from.file", filename);
	}
}
