/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.LocalDate

import groovy.transform.ToString
import nu.yona.server.YonaServer

@ToString(includeNames = true)
class Buddy
{
	final String receivingStatus
	final String sendingStatus
	final String lastStatusChangeTime
	final LocalDate lastMonitoredActivityDate
	final User user
	final String url
	final String goalsUrl
	final String dailyActivityReportsUrl
	final String weeklyActivityReportsUrl
	final String editUrl

	Buddy(def json)
	{
		this.receivingStatus = json.receivingStatus
		this.sendingStatus = json.sendingStatus
		this.lastStatusChangeTime = json.lastStatusChangeTime
		this.lastMonitoredActivityDate = (json.lastMonitoredActivityDate) ? YonaServer.parseIsoDateString(json.lastMonitoredActivityDate) : null
		if (json._embedded?."yona:user")
		{
			this.user = new User(json._embedded."yona:user")
		}
		this.goalsUrl = json._embedded?."yona:goals"?._links?.self?.href
		this.url = YonaServer.stripQueryString(json._links.self.href)
		this.dailyActivityReportsUrl = json._links?."yona:dailyActivityReports"?.href
		this.weeklyActivityReportsUrl = json._links?."yona:weeklyActivityReports"?.href
		this.editUrl = json._links?.edit?.href
	}

	def findActiveGoal(def activityCategoryUrl)
	{
		user.goals.find { it.activityCategoryUrl == activityCategoryUrl && !it.historyItem }
	}

	/**
	 * Finds a goal in the buddy context given the goal from the user context	*/
	Goal findGoal(Goal goal)
	{
		def goalId = goal.url.substring(goal.url.lastIndexOf('/') + 1)
		Goal matchingGoal = user.goals.find { it.url.endsWith(goalId) }
		assert matchingGoal
		return matchingGoal
	}
}
