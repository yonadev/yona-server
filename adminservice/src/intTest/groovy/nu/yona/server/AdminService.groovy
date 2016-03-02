/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.Service

class AdminService extends Service
{
	final static ACTIVITY_CATEGORIES_PATH = "/activityCategories/"
	JsonSlurper jsonSlurper = new JsonSlurper()

	AdminService()
	{
		super("yona.adminservice.url", "http://localhost:8080")
	}

	def getAllActivityCategories()
	{
		yonaServer.getResource(ACTIVITY_CATEGORIES_PATH)
	}
}
