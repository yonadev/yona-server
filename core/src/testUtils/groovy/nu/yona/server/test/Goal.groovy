/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

abstract class Goal
{
	final String activityCategoryName
	final String url
	final String editURL
	Goal(def json)
	{
		this.activityCategoryName = json.activityCategoryName
		this.url = json._links ? YonaServer.stripQueryString(json._links.self.href) : null
		this.editURL = json._links?.edit?.href
	}

	def abstract convertToJsonString()

	static def fromJSON(def json)
	{
		if(json["@type"] == "BudgetGoal")
		{
			return new BudgetGoal(json)
		}
		else if(json["@type"] == "TimeZoneGoal")
		{
			return new TimeZoneGoal(json)
		}
		else throw new RuntimeException("Unknown goal type: " + json["@type"])
	}

	def convertToJSON()
	{
		def jsonStr = convertToJsonString()

		return new JsonSlurper().parseText(jsonStr)
	}
}
