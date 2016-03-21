/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
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
