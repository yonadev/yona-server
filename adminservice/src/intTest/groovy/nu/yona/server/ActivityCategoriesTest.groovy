/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
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

	// See https://docs.hazelcast.org/docs/latest/manual/html-single/index.html#near-cache-invalidation
	// Default batch invalidation frequency is 10 seconds
	static final def cachePropagationTimeoutSeconds = 20

	@Shared
	def AdminService adminService = new AdminService()

	@Shared
	def AppService appService = new AppService()

	// NOTE: This test assumes that en-US is the client language
	def 'App service should sync activity categories cache with admin service'()
	{
		given:
		String programmingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Programmeren", "en-US" : "Programming"], false, ["programming", "scripting"], ["Eclipse", "Visual Studio"], ["nl-NL": "Programmeren van computers", "en-US" : "Programming computers"])
		def createProgrammingResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, programmingActivityCategoryJson)
		assertResponseStatusOk(createProgrammingResponse)
		String chessActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Schaken", "en-US" : "Chess"], false, ["chess"], ["Chess Free", "Analyze This", "Chess Opening Blunders"], ["nl-NL": "Schaken tegen mensen", "en-US" : "Chess against humans"])
		String cookingActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Koken", "en-US" : "Cooking"], false, ["cooking"], [], ["nl-NL": "Raadplegen van websites over koken", "en-US" : "Reading about cooking"])
		def createCookingResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, cookingActivityCategoryJson)
		assertResponseStatusOk(createCookingResponse)
		String gardeningActivityCategoryJson = createActivityCategoryJson(["nl-NL": "Tuinieren", "en-US" : "Gardening"], false, ["gardening"], [], ["nl-NL": "Raadplegen van websites over tuinieren", "en-US" : "Reading about gardening"])

		when:
		def updateResponse = adminService.yonaServer.updateResource(createProgrammingResponse.responseData._links.self.href, chessActivityCategoryJson)
		assertResponseStatusOk(updateResponse)
		def deleteResponse = adminService.yonaServer.deleteResource(createCookingResponse.responseData._links.self.href)
		assertResponseStatusOk(deleteResponse)
		def createResponse = adminService.yonaServer.createResource(AdminService.ACTIVITY_CATEGORIES_PATH, gardeningActivityCategoryJson)
		assertResponseStatusOk(createResponse)
		waitForCachePropagation("Gardening", "Reading about gardening")
		
		then:
		def getAllResponse = appService.getAllActivityCategoriesWithLanguage("en-US")
		def programmingCategory = appServicefindActivityCategoryByName(getAllResponse, "Programming")
		programmingCategory == null
		def chessCategory = appServicefindActivityCategoryByName(getAllResponse, "Chess")
		chessCategory != null
		def gardeningCategory = appServicefindActivityCategoryByName(getAllResponse, "Gardening")
		gardeningCategory != null
		def cookingCategory = appServicefindActivityCategoryByName(getAllResponse, "Cooking")
		cookingCategory == null
		
		cleanup:
		if (createCookingResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createCookingResponse.responseData._links.self.href)
		}
		if (createProgrammingResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createProgrammingResponse.responseData._links.self.href)
		}
		if (createResponse?.status == 200)
		{
			adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)
		}
	}

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
		def numActivityCategoriesBeforeAdd = adminService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()

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

		def getAllResponse = adminService.getAllActivityCategories()
		getAllResponse.responseData._embedded."yona:activityCategories".size() == numActivityCategoriesBeforeAdd + 1
		def programmingCategory = findActivityCategoryByName(getAllResponse, englishName)
		programmingCategory != null
		programmingCategory.applications as Set == apps
		programmingCategory.localizableDescription["en-US"] == englishDescription

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
		def programmingCategory = findActivityCategoryByName(adminService.getAllActivityCategories(), "Programming")

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

		def getAllResponse = adminService.getAllActivityCategories()
		def chessCategory = getAllResponse.responseData._embedded."yona:activityCategories".find{ it.localizableName["en-US"] == englishName }
		chessCategory != null
		chessCategory._links.self.href == programmingCategory._links.self.href
		chessCategory.applications as Set == apps
		chessCategory.localizableDescription["en-US"] == englishDescription

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
		def numActivityCategoriesBeforeDelete = adminService.getAllActivityCategories().responseData._embedded."yona:activityCategories".size()

		when:
		def response = adminService.yonaServer.deleteResource(createResponse.responseData._links.self.href)

		then:
		assertResponseStatusOk(response)

		def getAllResponse = adminService.getAllActivityCategories()
		getAllResponse.responseData._embedded."yona:activityCategories".size() == numActivityCategoriesBeforeDelete - 1
		findActivityCategoryByName(getAllResponse, "Programming") == null
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

	private findActivityCategoryByName(getAllResponse, englishName)
	{
		getAllResponse.responseData._embedded."yona:activityCategories".find{ it.localizableName["en-US"] == englishName }
	}

	private appServicefindActivityCategoryByName(getAllResponse, englishName)
	{
		getAllResponse.responseData._embedded."yona:activityCategories".find{ it.name == englishName }
	}

	private void waitForCachePropagation(originalCount)
	{
		for (int i = 0; i < cachePropagationTimeoutSeconds; i++)
		{
			def response = appService.getAllActivityCategoriesWithLanguage("en-US")
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
			def response = appService.getAllActivityCategoriesWithLanguage("en-US")
			assertResponseStatusOk(response)
			def category = appServicefindActivityCategoryByName(response, englishName)
			if (category != null && category.description == englishDescription)
			{
				return
			}
			sleep(1000)
		}
	}
}
