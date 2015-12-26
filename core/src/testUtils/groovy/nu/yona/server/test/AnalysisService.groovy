/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class AnalysisService extends Service
{
	final ANALYSIS_ENGINE_PATH = "/analysisEngine/"
	final RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT = "/relevantSmoothwallCategories/"

	JsonSlurper jsonSlurper = new JsonSlurper()

	AnalysisService ()
	{
		super("yona.analysisservice.url", "http://localhost:8080")
	}

	def getRelevantSmoothwallCategories()
	{
		yonaServer.getResource(ANALYSIS_ENGINE_PATH + RELEVANT_SMOOTHWALL_CATEGORIES_PATH_FRAGMENT)
	}

	def postToAnalysisEngine(user, categories, url)
	{
		def categoriesString = YonaServer.makeStringList(categories)
		postToAnalysisEngine("""{
					"vpnLoginID":"$user.vpnProfile.vpnLoginID",
					"categories": [$categoriesString],
					"url":"$url"
				}""")
	}

	def postToAnalysisEngine(jsonString)
	{
		yonaServer.postJson(ANALYSIS_ENGINE_PATH, jsonString)
	}
}
