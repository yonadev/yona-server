/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*
import nu.yona.server.test.AppService
import spock.lang.Shared
import spock.lang.Specification

class ActivityCategoriesTest extends Specification
{
	static final def cachePropagationTimeoutSeconds = 10

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
		assertResponseStatusOk(response)
		response.responseData._links.self.href == adminService.url + AdminService.ACTIVITY_CATEGORIES_PATH
		response.responseData._embedded?."yona:activityCategories" != null
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
		gamblingCategory.localizableDescription["en-US"] == "This challenge includes apps and sites like Poker and Blackjack"
		gamblingCategory.localizableDescription["nl-NL"] == "Deze challenge bevat apps en sites zoals Poker en Blackjack"
	}

	def 'Add programming activity category' ()
	{
		given:
		String englishName = "Programming"
		String dutchName = "Programmeren"
		boolean isNoGo = false
		def smoothwallCategories = ["programming", "scripting"] as Set
		def apps = ["Eclipse", "Visual Studio"] as Set
		String englishDescription = "Programming computers"
		String dutchDescription = "Programmeren van computers"
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": dutchName, "en-US" : englishName], isNoGo, smoothwallCategories, apps, ["nl-NL": dutchDescription, "en-US" : englishDescription])
		int numOfCategoriesInAppServiceBeforeAdd = appService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()

		when:
		def response = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)

		then:
		assertResponseStatusOk(response)
		response.responseData._links.self.href.startsWith(adminService.url)
		response.responseData.localizableName["en-US"] == englishName
		response.responseData.localizableName["nl-NL"] == dutchName
		response.responseData.mandatoryNoGo == isNoGo
		response.responseData.smoothwallCategories as Set == smoothwallCategories
		response.responseData.applications as Set == apps
		response.responseData.localizableDescription["en-US"] == englishDescription
		response.responseData.localizableDescription["nl-NL"] == dutchDescription

		def getResponse = adminService.yonaServer.getResource(response.responseData._links.self.href)
		assertResponseStatusOk(getResponse)
		getResponse.responseData._links.self.href.startsWith(adminService.url)
		getResponse.responseData.localizableName["en-US"] == englishName
		getResponse.responseData.localizableName["nl-NL"] == dutchName
		getResponse.responseData.mandatoryNoGo == isNoGo
		getResponse.responseData.smoothwallCategories as Set == smoothwallCategories
		getResponse.responseData.applications as Set == apps
		getResponse.responseData.localizableDescription["en-US"] == englishDescription
		getResponse.responseData.localizableDescription["nl-NL"] == dutchDescription

		waitForCachePropagation(numOfCategoriesInAppServiceBeforeAdd)
		def appServiceGetResponse = appService.getAllActivityCategories()
		appServiceGetResponse.responseData._embedded."yona:activityCategories".size() == numOfCategoriesInAppServiceBeforeAdd + 1
		def programmingCategory = findActivityCategoryByName(appServiceGetResponse, englishName)
		programmingCategory?.applications as Set == apps
		programmingCategory.description == englishDescription

		cleanup:
		if (response?.status == 200)
		{
			adminService.yonaServer.deleteResource(response.responseData._links.self.href)
		}
	}

	def 'Update activity category' ()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"], ["nl-NL": "Programmeren van computers", "en-US" : "Programming computers"])
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assertResponseStatusOk(createResponse)
		String englishName = "Chess"
		String dutchName = "Schaken"
		boolean isNoGo = true
		def smoothwallCategories = ["chess"] as Set
		def apps = ["Chess Free", "Analyze This", "Chess Opening Blunders"] as Set
		String englishDescription = "Chess against humans"
		String dutchDescription = "Schaken tegen mensen"
		String chessActivityCategoryJson = createActivityCategoryJson(["nl-NL": dutchName, "en-US" : englishName], isNoGo, smoothwallCategories, apps, ["nl-NL": dutchDescription, "en-US" : englishDescription])
		def programmingCategory = findActivityCategoryByName(appService.getAllActivityCategories(), "Programming")

		when:
		def response = adminService.yonaServer.updateResource(createResponse.responseData._links.self.href, chessActivityCategoryJson)

		then:
		assertResponseStatusOk(response)
		response.responseData._links.self.href == createResponse.responseData._links.self.href
		response.responseData.localizableName["en-US"] == englishName
		response.responseData.localizableName["nl-NL"] == dutchName
		response.responseData.mandatoryNoGo == isNoGo
		response.responseData.smoothwallCategories as Set == smoothwallCategories
		response.responseData.applications as Set == apps
		response.responseData.localizableDescription["en-US"] == englishDescription
		response.responseData.localizableDescription["nl-NL"] == dutchDescription

		def getResponse = adminService.yonaServer.getResource(createResponse.responseData._links.self.href)
		assertResponseStatusOk(getResponse)
		getResponse.responseData._links.self.href == createResponse.responseData._links.self.href
		getResponse.responseData.localizableName["en-US"] == englishName
		getResponse.responseData.localizableName["nl-NL"] == dutchName
		getResponse.responseData.mandatoryNoGo == isNoGo
		getResponse.responseData.smoothwallCategories as Set == smoothwallCategories
		getResponse.responseData.applications as Set == apps
		getResponse.responseData.localizableDescription["en-US"] == englishDescription
		getResponse.responseData.localizableDescription["nl-NL"] == dutchDescription

		waitForCachePropagation(englishName, englishDescription)
		def chessCategory = findActivityCategoryByName(appService.getAllActivityCategories(), englishName)
		chessCategory._links.self.href == programmingCategory._links.self.href
		chessCategory.applications as Set == apps
		chessCategory.description == englishDescription

		cleanup:
		if (createResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)
		}
	}

	def 'Delete programming activity category' ()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"], ["nl-NL": "Programmeren van computers", "en-US" : "Programming computers"])
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assertResponseStatusOk(createResponse)
		def numActivityCategories = adminService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()
		int numOfCategoriesInAppServiceBeforeDelete = appService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()

		when:
		def response = adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)

		then:
		assertResponseStatusOk(response)
		adminService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size() == numActivityCategories - 1

		waitForCachePropagation(numOfCategoriesInAppServiceBeforeDelete)
		def appServiceGetResponse = appService.getAllActivityCategories()
		appServiceGetResponse.responseData._embedded."yona:activityCategories".size() == numOfCategoriesInAppServiceBeforeDelete - 1
		findActivityCategoryByName(appServiceGetResponse, "Programming") == null
	}

	def 'Try add duplicate English name' ()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Zomaar wat", "en-US" : "Gambling"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"], ["nl-NL": "Programmeren van computers", "en-US" : "Programming computers"])
		when:
		def response = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.activitycategory.duplicate.name"
	}

	def 'Try update to duplicate Dutch name' ()
	{
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"], ["nl-NL": "Programmeren van computers", "en-US" : "Programming computers"])
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assertResponseStatusOk(createResponse)
		String englishName = "Just something"
		String dutchName = "Gokken"
		boolean isNoGo = true
		def smoothwallCategories = ["chess"]
		def apps = ["Chess Free", "Analyze This", "Chess Opening Blunders"]
		String englishDescription = "Programming computers"
		String dutchDescription = "Programmeren van computers"
		String chessActivityCategoryJson = createActivityCategoryJson(["nl-NL": dutchName, "en-US" : englishName], isNoGo, smoothwallCategories, apps, ["nl-NL": dutchDescription, "en-US" : englishDescription])
		when:
		def response = adminService.yonaServer.updateResource(createResponse.responseData._links.self.href, chessActivityCategoryJson)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.activitycategory.duplicate.name"

		cleanup:
		if (createResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)
		}
	}

	private String createActivityCategoryJson(localizableName, boolean mandatoryNoGo, smoothwallCategories, applications, localizableDescription)
	{
		String localizableNameString = YonaServer.makeStringMap(localizableName)
		String smoothwallCategoriesString = YonaServer.makeStringList(smoothwallCategories)
		String applicationsString = YonaServer.makeStringList(applications)
		String localizableDescriptionString = YonaServer.makeStringMap(localizableDescription)
		def json = """{
			"localizableName": {$localizableNameString},
			"mandatoryNoGo": $mandatoryNoGo,
			"smoothwallCategories": [$smoothwallCategoriesString],
			"applications": [$applicationsString],
			"localizableDescription": {$localizableDescriptionString}
		}"""
		return json
	}

	private findActivityCategoryByName(response, name)
	{
		response.responseData._embedded."yona:activityCategories".find{ it.name == name }
	}

	private void waitForCachePropagation(originalCount)
	{
		for (int i = 0; i < cachePropagationTimeoutSeconds; i++)
		{
			def response = appService.getAllActivityCategories()
			assertResponseStatusOk(response)
			if (response.responseData._embedded."yona:activityCategories".size() != originalCount)
			{
				return
			}
			sleep(1000)
		}
	}

	private void waitForCachePropagation(englishName, englishDescription)
	{
		for (int i = 0; i < cachePropagationTimeoutSeconds; i++)
		{
			def response = appService.getAllActivityCategories()
			assertResponseStatusOk(response)
			def category = findActivityCategoryByName(response, englishName)
			if (category != null && category.description == englishDescription)
			{
				return
			}
			sleep(1000)
		}
	}
}
