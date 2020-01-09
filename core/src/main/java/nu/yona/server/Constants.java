/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class Constants
{
	public static final String ISO_DATE_PATTERN = "yyyy-MM-dd";

	public static final String ISO_DATE_TIME_PATTERN = ISO_DATE_PATTERN + "'T'HH:mm:ss.SSSZ";

	public static final Marker ALERT_MARKER = MarkerFactory.getMarker("ALERT");

	private Constants()
	{
		// No instances
	}
}
