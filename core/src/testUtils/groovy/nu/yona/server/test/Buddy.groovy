/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class Buddy
{
	final String nickname
	final String receivingStatus
	final String sendingStatus
	final User user
	final List<Goal> goals
	final String url
	final String dailyActivityReportsUrl
	final String weeklyActivityReportsUrl
	final String editURL
	Buddy(def json)
	{
		this.nickname = json.nickname
		this.receivingStatus = json.receivingStatus
		this.sendingStatus = json.sendingStatus
		if (json._embedded?."yona:user")
		{
			this.user = new User(json._embedded."yona:user")
		}
		this.goals = (json._embedded?."yona:goals"?._embedded?."yona:goals") ? json._embedded."yona:goals"._embedded."yona:goals".collect{Goal.fromJSON(it)} : null
		this.url = YonaServer.stripQueryString(json._links.self.href)
		this.dailyActivityReportsUrl = json._links?."yona:dailyActivityReports"?.href
		this.weeklyActivityReportsUrl = json._links?."yona:weeklyActivityReports"?.href
		this.editURL = json._links?.edit?.href
	}

	/**
	 * Finds a goal in the buddy context given the goal from the user context
	 */
	Goal findGoal(Goal goal)
	{
		def goalID = goal.url.substring(goal.url.lastIndexOf('/') + 1)
		Goal matchingGoal = goals.find{it.url.endsWith(goalID)}
		assert matchingGoal
		return matchingGoal
	}
}