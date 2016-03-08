/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class TimeZoneGoal extends Goal
{
	final String[] zones
	TimeZoneGoal(def json)
	{
		super(json)

		this.zones = json.zones
	}

	def convertToJsonString()
	{
		def selfLinkString = (url) ? """"_links":{"self":{"href":"$url"}},""" : ""
		def zonesString = YonaServer.makeStringList(zones)
		return """{
			$selfLinkString,
			"@type":"TimeZoneGoal",
			"activityCategoryName":"${activityCategoryName}",
			"zones":[
				${zonesString}
			]
		}"""
	}

	public static TimeZoneGoal createInstance(activityCategoryName, zones)
	{
		new TimeZoneGoal(["activityCategoryName": activityCategoryName, "zones": zones])
	}
}
