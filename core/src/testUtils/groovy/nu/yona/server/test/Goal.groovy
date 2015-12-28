/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class Goal
{
	final String activityCategoryName
	Goal(def json)
	{
		this.activityCategoryName = json.activityCategoryName
	}
	
	def convertToJsonString()
	{
		return """{
			"activityCategoryName":"${activityCategoryName}"
		}"""
	}
	
	public static Goal createInstance(activityCategoryName)
	{
		new Goal(["activityCategoryName": activityCategoryName])
	}
}
