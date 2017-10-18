/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.ZonedDateTime

import groovy.json.*
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
		def eventTimeString = (eventTime) ? YonaServer.toIsoDateTimeString(eventTime) : null
		def eventTimeProperty = (eventTimeString) ? """"eventTime" : "$eventTimeString",""" : ""
		postToAnalysisEngine(user.vpnProfile.vpnLoginId, """{
					$eventTimeProperty
					"categories": [$categoriesString],
					"url":"$url"
				}""")
	}

	def postToAnalysisEngine(String vpnLoginId, jsonString)
	{
		yonaServer.postJson(USER_ANONYMIZED_PATH + vpnLoginId + "/networkActivity/", jsonString)
	}
	void setEnableStatistics(def enable)
	{
		def response = yonaServer.createResource("/hibernateStatistics/enable/", "{}", [:], ["enable" : enable])
		assert response.status == 200 : "Ensure the server stats are enabled (run with -Dyona.enableHibernateStatsAllowed=true)"
	}

	def getResource(path, headers = [:], parameters = [:])
	{
		yonaServer.getResource(path, headers, parameters)
	}

	void resetStatistics()
	{
		def response = getResource("/hibernateStatistics/", [:], ["reset" : "true"])
		assert response.status == 200
	}

	void clearCaches()
	{
		def response = yonaServer.createResource("/hibernateStatistics/clearCaches/", "{}", [:], [:])
		assert response.status == 200
	}

	def getStatistics()
	{
		def response = getResource("/hibernateStatistics/", [:], ["reset" : "false"])
		assert response.status == 200
		response.responseData
	}
}
