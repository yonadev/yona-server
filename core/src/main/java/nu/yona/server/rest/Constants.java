/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

public final class Constants
{
	public static final String PASSWORD_HEADER = "Yona-Password";
	public static final String NEW_DEVICE_REQUEST_PASSWORD_HEADER = "Yona-NewDeviceRequestPassword";
	public static final String APP_VERSION_HEADER = "Yona-App-Version";
	public static final String APP_OS_MDC_KEY = "yona.app.os";
	public static final String APP_VERSION_CODE_MDC_KEY = "yona.app.versionCode";
	public static final String APP_VERSION_NAME_MDC_KEY = "yona.app.versionName";

	private Constants()
	{
		// No instances
	}
}
