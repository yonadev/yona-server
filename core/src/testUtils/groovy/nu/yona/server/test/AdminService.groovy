/*******************************************************************************
 * Copyright (c) 2017, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

class AdminService extends Service
{
	final SYSTEM_MESSAGES_PATH = "/systemMessages/"

	AdminService()
	{
		super("yona.adminservice.url", "http://localhost:8080")
	}

	def postSystemMessage(String messageText)
	{
		yonaServer.postData(SYSTEM_MESSAGES_PATH, "application/x-www-form-urlencoded", null, "message=" + URLEncoder.encode(messageText, "UTF-8"))
	}
}
