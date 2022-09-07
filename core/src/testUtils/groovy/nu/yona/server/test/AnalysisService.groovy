/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.ZonedDateTime

import nu.yona.server.YonaServer

class AnalysisService extends Service
{
	final USER_ANONYMIZED_PATH = "/userAnonymized/"
	final RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT = "/relevantSmoothwallCategories/"

	AnalysisService()
	{
		super("yona.analysisservice.url", "http://localhost:8080")
	}

	def getRelevantSmoothwallCategories()
	{
		yonaServer.getJson(RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT)
	}

	def postToAnalysisEngine(Device device, categories, url, ZonedDateTime eventTime = null)
	{
		def categoriesString = YonaServer.makeStringList(categories)
		def eventTimeString = (eventTime) ? YonaServer.toIsoDateTimeString(eventTime) : null
		def eventTimeProperty = (eventTimeString) ? """"eventTime" : "$eventTimeString",""" : ""
		def dollarIndex = device.vpnProfile.vpnLoginId.indexOf("\$")
		def userAnonymizedId = device.vpnProfile.vpnLoginId[0..dollarIndex - 1]
		def deviceIndex = device.vpnProfile.vpnLoginId[dollarIndex + 1..-1]
		postToAnalysisEngine(userAnonymizedId, """{
					"deviceIndex": $deviceIndex,
					$eventTimeProperty
					"categories": [$categoriesString],
					"url":"$url"
				}""")
	}

	def postToAnalysisEngine(String vpnLoginId, jsonString)
	{
		yonaServer.postJson(USER_ANONYMIZED_PATH + vpnLoginId + "/networkActivity/", jsonString)
	}
}
