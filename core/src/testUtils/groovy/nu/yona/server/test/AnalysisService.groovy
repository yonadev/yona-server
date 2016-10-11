/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*

import java.time.ZonedDateTime

import nu.yona.server.YonaServer

class AnalysisService extends Service
{
	final USER_ANONYMIZED_PATH = "/userAnonymized/"
	final RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT = "/relevantSmoothwallCategories/"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AnalysisService ()
	{
		super("yona.analysisservice.url", "http://localhost:8080")
	}

	def getRelevantSmoothwallCategories()
	{
		yonaServer.getResource(RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT)
	}

	def postToAnalysisEngine(user, categories, url, ZonedDateTime eventTime = null)
	{
		def categoriesString = YonaServer.makeStringList(categories)
		def eventTimeString = (eventTime) ? YonaServer.toIsoDateString(eventTime) : null
		def eventTimeProperty = (eventTimeString) ? """"eventTime" : "$eventTimeString",""" : ""
		postToAnalysisEngine(user.vpnProfile.vpnLoginID, """{
					$eventTimeProperty
					"categories": [$categoriesString],
					"url":"$url"
				}""")
	}
	def postToAnalysisEngine(String vpnLoginID, jsonString)
	{
		yonaServer.postJson(USER_ANONYMIZED_PATH + vpnLoginID + "/networkActivity/", jsonString)
	}
}
