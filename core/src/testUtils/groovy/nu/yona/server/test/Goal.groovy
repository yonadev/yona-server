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
	Goal(def json)
	{
		this.activityCategoryName = json.activityCategoryName
		this.url = json._links ? YonaServer.stripQueryString(json._links.self.href) : null
	}

	def abstract convertToJsonString()

	def convertToJSON()
	{
		def jsonStr = convertToJsonString()

		return new JsonSlurper().parseText(jsonStr)
	}

	static def fromJson(def json)
	{
		if(json['@class'] == 'budgetGoal')
		{
			return new BudgetGoal(json)
		}
		throw new RuntimeException("Goal.fromJson not implemented for goal subclass: " + json['@class'])
	}
}
