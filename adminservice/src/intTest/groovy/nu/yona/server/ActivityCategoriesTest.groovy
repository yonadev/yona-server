/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AppService
import spock.lang.Shared
import spock.lang.Specification

class ActivityCategoriesTest extends Specification
{
	@Shared
	def AdminService adminService = new AdminService()

	@Shared
	def AppService appService = new AppService()

	def 'Get all activity categories loaded from file'()
	{
		given:

		when:
		def response = adminService.getAllActivityCategories()

		then:
		response.status == 200
		response.responseData._links.self.href == adminService.url + AdminService.ACTIVITY_CATEGORIES_PATH
		response.responseData._embedded."yona:activityCategories".size() > 0
		def gamblingCategory = response.responseData._embedded."yona:activityCategories".find
		{
			it._links.self.href ==~ /.*192d69f4-8d3e-499b-983c-36ca97340ba9.*/
		}
		gamblingCategory._links.self.href.startsWith(adminService.url)
		gamblingCategory.localizableName["en-US"] == "Gambling"
		gamblingCategory.localizableName["nl-NL"] == "Gokken"
		gamblingCategory.mandatoryNoGo == true
		gamblingCategory.smoothwallCategories as Set == ["Gambling", "KS-Gokken", "lotto"] as Set
		gamblingCategory.applications as Set == ["Lotto App", "Poker App"] as Set
	}

	def 'Add programming activity category' ()
	{
		given:
		def englishName = "Programming"
		String dutchName = "Programmeren"
		boolean isNoGo = false
		def smoothwallCategories = ["programming", "scripting"] as Set
		def apps = ["Eclipse", "Visual Studio"] as Set
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": dutchName, "en-US" : englishName], isNoGo, smoothwallCategories, apps)
		int numOfCategoriesInAppServiceBeforeAdd = appService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()

		when:
		def response = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)

		then:
		response.status == 200
		response.responseData._links.self.href.startsWith(adminService.url)
		response.responseData.localizableName["en-US"] == englishName
		response.responseData.localizableName["nl-NL"] == dutchName
		response.responseData.mandatoryNoGo == isNoGo
		response.responseData.smoothwallCategories as Set == smoothwallCategories
		response.responseData.applications as Set == apps

		def getResponse = adminService.yonaServer.getResource(response.responseData._links.self.href)
		getResponse.status == 200
		getResponse.responseData._links.self.href.startsWith(adminService.url)
		getResponse.responseData.localizableName["en-US"] == englishName
		getResponse.responseData.localizableName["nl-NL"] == dutchName
		getResponse.responseData.mandatoryNoGo == isNoGo
		getResponse.responseData.smoothwallCategories as Set == smoothwallCategories
		getResponse.responseData.applications as Set == apps

		def appServiceGetResponse = appService.getAllActivityCategories()
		appServiceGetResponse.responseData._embedded."yona:activityCategories".size() == numOfCategoriesInAppServiceBeforeAdd + 1
		def programmingCategory = findActivityCategoryByName(appServiceGetResponse, englishName)
		programmingCategory?.applications as Set == apps

		cleanup:
		if (response?.status == 200)
		{
			adminService.yonaServer.deleteResource(response.responseData._links.self.href)
		}
	}

	def 'Update activity category' ()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"])
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assert createResponse.status == 200
		def englishName = "Chess"
		String dutchName = "Schaken"
		boolean isNoGo = true
		def smoothwallCategories = ["chess"] as Set
		def apps = ["Chess Free", "Analyze This", "Chess Opening Blunders"] as Set
		String chessActivityCategoryJson = createActivityCategoryJson(["nl-NL": dutchName, "en-US" : englishName], isNoGo, smoothwallCategories, apps)
		def programmingCategory = findActivityCategoryByName(appService.getAllActivityCategories(), "Programming")

		when:
		def response = adminService.yonaServer.updateResource(createResponse.responseData._links.self.href, chessActivityCategoryJson)

		then:
		response.status == 200
		response.responseData._links.self.href == createResponse.responseData._links.self.href
		response.responseData.localizableName["en-US"] == englishName
		response.responseData.localizableName["nl-NL"] == dutchName
		response.responseData.mandatoryNoGo == isNoGo
		response.responseData.smoothwallCategories as Set == smoothwallCategories
		response.responseData.applications as Set == apps

		def getResponse = adminService.yonaServer.getResource(createResponse.responseData._links.self.href)
		getResponse.status == 200
		getResponse.responseData._links.self.href == createResponse.responseData._links.self.href
		getResponse.responseData.localizableName["en-US"] == englishName
		getResponse.responseData.localizableName["nl-NL"] == dutchName
		getResponse.responseData.mandatoryNoGo == isNoGo
		getResponse.responseData.smoothwallCategories as Set == smoothwallCategories
		getResponse.responseData.applications as Set == apps

		def chessCategory = findActivityCategoryByName(appService.getAllActivityCategories(), englishName)
		chessCategory._links.self.href == programmingCategory._links.self.href
		chessCategory.applications as Set == apps

		cleanup:
		if (createResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)
		}
	}

	def 'Delete programming activity category' ()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"])
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assert createResponse.status == 200
		def numActivityCategories = adminService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()
		int numOfCategoriesInAppServiceBeforeDelete = appService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()

		when:
		def response = adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)

		then:
		response.status == 200
		adminService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size() == numActivityCategories - 1

		def appServiceGetResponse = appService.getAllActivityCategories()
		appServiceGetResponse.responseData._embedded."yona:activityCategories".size() == numOfCategoriesInAppServiceBeforeDelete - 1
		findActivityCategoryByName(appServiceGetResponse, "Programming") == null
	}

	def 'Try add duplicate English name' ()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Zomaar wat", "en-US" : "Gambling"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"])
		when:
		def response = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)

		then:
		response.status == 400
		response.responseData.code == "error.activitycategory.duplicate.name"
	}

	def 'Try update to duplicate Dutch name' ()
	{
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"])
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assert createResponse.status == 200
		def englishName = "Just something"
		String dutchName = "Gokken"
		boolean isNoGo = true
		def smoothwallCategories = ["chess"]
		def apps = ["Chess Free", "Analyze This", "Chess Opening Blunders"]
		String chessActivityCategoryJson = createActivityCategoryJson(["nl-NL": dutchName, "en-US" : englishName], isNoGo, smoothwallCategories, apps)
		when:
		def response = adminService.yonaServer.updateResource(createResponse.responseData._links.self.href, chessActivityCategoryJson)

		then:
		response.status == 400
		response.responseData.code == "error.activitycategory.duplicate.name"

		cleanup:
		if (createResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)
		}
	}

	private String createActivityCategoryJson(localizableNames, boolean mandatoryNoGO, smoothwallCategories, applications)
	{
		String localizableNamesString = YonaServer.makeStringMap(localizableNames)
		String smoothwallCategoriesString = YonaServer.makeStringList(smoothwallCategories)
		String applicationsString = YonaServer.makeStringList(applications)
		def json = """{
			"localizableName": {$localizableNamesString},
			"mandatoryNoGo": $mandatoryNoGO,
			"smoothwallCategories": [$smoothwallCategoriesString],
			"applications": [$applicationsString]
		}"""
		return json
	}

	private findActivityCategoryByName(response, name)
	{
		response.responseData._embedded."yona:activityCategories".find{ it.name == name }
	}
}
