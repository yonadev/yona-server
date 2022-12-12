/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.ZonedDateTime

import nu.yona.server.YonaServer

abstract class Goal
{
	final String activityCategoryUrl
	final String url
	final String editUrl
	ZonedDateTime creationTime
	final boolean historyItem

	Goal(def json)
	{
		this.creationTime = (json.creationTime instanceof ZonedDateTime) ? json.creationTime : null
		// Only set the creation time when explicitly provided
		this.activityCategoryUrl = (json.activityCategoryUrl) ?: json._links."yona:activityCategory".href
		this.url = json._links ? json._links.self.href : null
		this.editUrl = json._links?.edit?.href
		this.historyItem = json.historyItem
	}

	def abstract convertToJsonString()

	def getId()
	{
		YonaServer.stripQueryString(url)[-36..-1]
	}

	static def fromJson(def json)
	{
		if (json["@type"] == "BudgetGoal")
		{
			return new BudgetGoal(json)
		}
		else if (json["@type"] == "TimeZoneGoal")
		{
			return new TimeZoneGoal(json)
		}
		else
		{
			throw new RuntimeException("Unknown goal type: " + json["@type"])
		}
	}

	protected def buildSelfLinkString()
	{
		(url) ? """
				"self":
					{
						"href":"$url"
					},""" : ""
	}

	protected def buildCreationTimeString()
	{
		def creationTimeString = (creationTime) ? YonaServer.toIsoDateTimeString(creationTime) : null
		(creationTimeString) ? """"creationTime" : "$creationTimeString",""" : ""
	}
}
