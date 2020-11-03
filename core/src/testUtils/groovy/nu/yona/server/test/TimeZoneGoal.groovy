/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.ZonedDateTime

import groovy.transform.ToString
import nu.yona.server.YonaServer

@ToString(includeSuper = true, includeNames = true)
class TimeZoneGoal extends Goal
{
	final String[] zones
	final int[] spreadCells

	TimeZoneGoal(def json)
	{
		super(json)

		this.zones = json.zones
		this.spreadCells = json.spreadCells
	}

	def convertToJsonString()
	{
		def zonesString = YonaServer.makeStringList(zones)
		def selfLinkString = buildSelfLinkString()
		def creationTimeString = buildCreationTimeString()
		return """{
			"@type":"TimeZoneGoal",
			$creationTimeString
			"zones":[
				${zonesString}
			],
			"_links":
				{
					$selfLinkString
					"yona:activityCategory":
						{
							"href":"$activityCategoryUrl"
						}
				}
		}"""
	}

	public static TimeZoneGoal createInstance(activityCategoryUrl, zones)
	{
		createInstance(null, activityCategoryUrl, zones)
	}

	public static TimeZoneGoal createInstance(ZonedDateTime creationTime, activityCategoryUrl, zones)
	{
		new TimeZoneGoal([creationTime: creationTime, activityCategoryUrl: activityCategoryUrl, zones: zones])
	}

	public static TimeZoneGoal createInstance(TimeZoneGoal originalGoal, ZonedDateTime creationTime, zones)
	{
		new TimeZoneGoal([creationTime: creationTime, activityCategoryUrl: originalGoal.activityCategoryUrl, zones: zones])
	}
}
