/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.Duration
import java.time.ZonedDateTime

import nu.yona.server.test.AnalysisService
import nu.yona.server.test.AppService
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.User
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractAppServiceIntegrationTest extends Specification
{
	@Shared
	def AnalysisService analysisService = new AnalysisService()

	@Shared
	def AppService appService = new AppService()

	@Shared
	private String baseTimestamp = createBaseTimestamp()

	@Shared
	private int sequenceNumber = 0

	@Shared
	public String NEWS_ACT_CAT_URL = appService.composeActivityCategoryUrl("743738fd-052f-4532-a2a3-ba60dcb1adbf")

	@Shared
	public String GAMBLING_ACT_CAT_URL = appService.composeActivityCategoryUrl("192d69f4-8d3e-499b-983c-36ca97340ba9")

	@Shared
	public String SOCIAL_ACT_CAT_URL = appService.composeActivityCategoryUrl("27395d17-7022-4f71-9daf-f431ff4f11e8")

	User addRichard()
	{
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestamp")
		richard = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, richard)
		def response = appService.addGoal(richard, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assert response.status == 201
		return richard
	}

	User addBob()
	{
		def bob = appService.addUser(appService.&assertUserCreationResponseDetails, "B o b", "Bob", "Dunn", "BD",
				"+$timestamp")
		bob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, bob)
		def response = appService.addGoal(bob, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assert response.status == 201
		return bob
	}

	def addRichardAndBobAsBuddies()
	{
		def richard = addRichard()
		def bob = addBob()
		appService.makeBuddies(richard, bob)
		return ["richard" : richard, "bob" : bob]
	}

	private static String createBaseTimestamp()
	{
		YonaServer.getTimeStamp()
	}

	protected String getTimestamp()
	{
		int num = sequenceNumber++
		return "$baseTimestamp$num"
	}

	def assertEquals(dateTimeString, ZonedDateTime comparisonDateTime, int epsilonSeconds = 10)
	{
		// Example date string: 2016-02-23T21:28:58.556+0000
		assert dateTimeString ==~ /[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}\+\d{4}/
		ZonedDateTime dateTime = YonaServer.parseIsoDateString(dateTimeString)
		int epsilonMilliseconds = epsilonSeconds * 1000

		assert dateTime.isAfter(comparisonDateTime.minus(Duration.ofMillis(epsilonMilliseconds)))
		assert dateTime.isBefore(comparisonDateTime.plus(Duration.ofMillis(epsilonMilliseconds)))

		return true
	}

	def findGoal(def response, def activityCategoryUrl)
	{
		response.responseData._embedded."yona:goals".find{ it._links."yona:activityCategory".href == activityCategoryUrl }
	}
}
