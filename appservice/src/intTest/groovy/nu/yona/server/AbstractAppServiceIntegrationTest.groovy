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
import nu.yona.server.test.AppActivity
import nu.yona.server.test.AppService
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.Goal
import nu.yona.server.test.TimeZoneGoal
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

	@Shared
	private def fullDay = [ Sun: "SUNDAY", Mon : "MONDAY", Tue : "TUESDAY", Wed : "WEDNESDAY", Thu : "THURSDAY", Fri: "FRIDAY", Sat: "SATURDAY" ]

	User addRichard()
	{
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestamp")
		richard = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, richard)
		def response = appService.addGoal(richard, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assert response.status == 201
		return appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
	}

	User addBob()
	{
		def bob = appService.addUser(appService.&assertUserCreationResponseDetails, "B o b", "Bob", "Dunn", "BD",
				"+$timestamp")
		bob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, bob)
		def response = appService.addGoal(bob, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assert response.status == 201
		return appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, bob.url, true, bob.password)
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

	def findActiveGoal(def response, def activityCategoryUrl)
	{
		response.responseData._embedded."yona:goals".find{ it._links."yona:activityCategory".href == activityCategoryUrl && !it.historyItem }
	}

	def findGoalsIncludingHistoryItems(def response, def activityCategoryUrl)
	{
		response.responseData._embedded."yona:goals".findAll{ it._links."yona:activityCategory".href == activityCategoryUrl }
	}

	void setGoalCreationTime(User user, activityCategoryURL, relativeCreationDateTimeString)
	{
		Goal goal = user.findActiveGoal(activityCategoryURL)
		goal.creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		def response = appService.updateGoal(user, goal.url, goal)
		assert response.status == 200
	}

	void addTimeZoneGoal(User user, activityCategoryURL, zones, relativeCreationDateTimeString)
	{
		ZonedDateTime creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		appService.addGoal(appService.&assertResponseStatusCreated, user, TimeZoneGoal.createInstance(creationTime, activityCategoryURL, zones.toArray()))
	}

	void reportAppActivity(User user, def appName, def relativeStartDateTimeString, relativeEndDateTimeString)
	{
		reportAppActivities(user, createAppActivity(appName, relativeStartDateTimeString, relativeEndDateTimeString))
	}

	AppActivity createAppActivity(def appName, def relativeStartDateTimeString, relativeEndDateTimeString)
	{
		def startDateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeStartDateTimeString)
		def endDateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeEndDateTimeString)
		AppActivity.singleActivity(appName, startDateTime, endDateTime)
	}

	void reportAppActivities(User user, def appActivities)
	{
		appActivities.collect
		{
			def response = appService.postAppActivityToAnalysisEngine(user, it)
			assert response.status == 200
		}
	}
	void reportNetworkActivity(User user, def categories, def url, relativeDateTimeString)
	{
		analysisService.postToAnalysisEngine(user, categories, url, YonaServer.relativeDateTimeStringToZonedDateTime(relativeDateTimeString))
	}

	void assertWeekOverviewBasics(response, numberOfReportedGoals, expectedTotalElements)
	{
		def expectedPageSize = 2
		assert response.status == 200
		assert response.responseData.page
		assert response.responseData.page.size == expectedPageSize
		assert response.responseData.page.totalElements == expectedTotalElements
		assert response.responseData._embedded?."yona:weekActivityOverviews"?.size() == numberOfReportedGoals.size()
		assert response.responseData._links?.self?.href != null

		numberOfReportedGoals.eachWithIndex
		{ numberOfGoals, weekIndex ->
			assert response.responseData._embedded."yona:weekActivityOverviews"[weekIndex]?.date =~ /\d{4}\-W\d{2}/
			assert response.responseData._embedded."yona:weekActivityOverviews"[weekIndex].timeZoneId == "Europe/Amsterdam"
			assert response.responseData._embedded."yona:weekActivityOverviews"[weekIndex].weekActivities?.size() == numberOfGoals
			// YD-203 assert response.responseData._embedded."yona:weekActivityOverviews"[weekIndex]._links?.self?.href
		}
	}

	void assertNumberOfReportedDaysForGoalInWeekOverview(weekActivityOverview, goalUrl, numberOfReportedDays)
	{
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goalUrl}
		assert weekActivityForGoal.spread == null // Only in detail
		assert weekActivityForGoal.totalActivityDurationMinutes == null // Only in detail
		assert weekActivityForGoal.totalMinutesBeyondGoal == null // Only for day
		assert weekActivityForGoal.date == null // Only on week overview level
		assert weekActivityForGoal.timeZoneId == null // Only on week overview level
		assert weekActivityForGoal._links."yona:goal"
		assert weekActivityForGoal._links."yona:weekDetails"
		assert weekActivityForGoal._links.self == null  // This is not a top level or embedded resource
		assert weekActivityForGoal?.dayActivities.size() == numberOfReportedDays
	}

	void assertDayInWeekOverviewForGoal(weekActivityOverview, goalUrl, expectedValues, shortDay)
	{
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goalUrl)
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goalUrl}
		assert weekActivityForGoal.dayActivities[fullDay[shortDay]]
		def dayActivityForGoal = weekActivityForGoal.dayActivities[fullDay[shortDay]]
		assert dayActivityForGoal.spread == null // Only in detail
		assert dayActivityForGoal.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread)
		assert dayActivityForGoal.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert dayActivityForGoal.date == null // Only on week overview level
		assert dayActivityForGoal.timeZoneId == null // Only on week overview level
		assert dayActivityForGoal._links."yona:goal" == null //already present on week
		assert dayActivityForGoal._links."yona:dayDetails"
		assert dayActivityForGoal._links.self == null  // This is not a top level or embedded resource
	}

	private def assertDayOverviewForGoal(response, goalUrl, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goalUrl)
		def dayActivityOverview = response.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		assert dayActivityOverview?.date =~ /\d{4}\-\d{2}\-\d{2}/
		assert dayActivityOverview.timeZoneId == "Europe/Amsterdam"
		assert dayActivityOverview.dayActivities?.size() == expectedValues[shortDay].size()
		// YD-203 assert dayActivityOverview._links?.self?.href
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goalUrl}
		assert dayActivityForGoal.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread))
		assert dayActivityForGoal.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert dayActivityForGoal.date == null // Only on day overview level
		assert dayActivityForGoal.timeZoneId == null // Only on day overview level
		assert dayActivityForGoal._links."yona:dayDetails"
		assert dayActivityForGoal._links.self == null  // This is not a top level or embedded resource
		return dayActivityForGoal
	}

	void assertDayOverviewForTimeZoneGoal(response, goalUrl, expectedValues, weeksBack, shortDay)
	{
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goalUrl)
		def dayActivityForTimeZoneGoal = assertDayOverviewForGoal(response, goalUrl, expectedValues, weeksBack, shortDay)
		assert dayActivityForTimeZoneGoal?.spread.size() == 96
	}

	void assertDayOverviewForBudgetGoal(response, goalUrl, expectedValues, weeksBack, shortDay)
	{
		def dayActivityForBudgetGoal = assertDayOverviewForGoal(response, goalUrl, expectedValues, weeksBack, shortDay)
		assert dayActivityForBudgetGoal?.spread == null
	}

	void assertDayOverviewBasics(response, expectedSize, expectedTotalElements, expectedPageSize = 3)
	{
		assert response.status == 200
		assert response.responseData._embedded?."yona:dayActivityOverviews"?.size() == expectedSize
		assert response.responseData.page
		assert response.responseData.page.size == expectedPageSize
		assert response.responseData.page.totalElements == expectedTotalElements
		assert response.responseData._links?.self?.href
	}

	void assertWeekDetailForGoal(User user, weekActivityOverview, String goalUrl, expectedValues)
	{
		def totalDurationMinutes = 0
		expectedValues.each { it.value.findAll{it.goalUrl == goalUrl}.each {it.data.spread.each { totalDurationMinutes += it.value }}}
		assert weekActivityOverview.weekActivities
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goalUrl}
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl = weekActivityForGoal?._links?."yona:weekDetails"?.href
		def response = appService.getResourceWithPassword(weekActivityDetailUrl, user.password)
		assert response.status == 200
		assert response.responseData.spread?.size() == 96
		def expectedSpread = (0..95).collect { 0 }
		expectedValues.each { it.value.findAll{it.goalUrl == goalUrl}.each {it.data.spread.each { expectedSpread[it.key] += it.value }}}
		assert response.responseData.spread == expectedSpread
		assert response.responseData.totalActivityDurationMinutes == totalDurationMinutes
		assert response.responseData.date =~ /\d{4}\-W\d{2}/
		assert response.responseData.timeZoneId == "Europe/Amsterdam"
		assert response.responseData._links?."yona:goal"
		def activeDays = 0
		expectedValues.each { activeDays += it.value.findAll{it.goalUrl == goalUrl}.size()}
		assert response.responseData.dayActivities?.size() == activeDays
		expectedValues.each {
			def day = it.key
			it.value.findAll{it.goalUrl == goalUrl}.each
			{
				def expectedDataForGoalOnDay = it.data
				assert response.responseData.dayActivities[fullDay[day]]
				def dayActivityForGoal = response.responseData.dayActivities[fullDay[day]]
				assert dayActivityForGoal.spread == null // Only in detail
				def expectedDayDurationMinutes = calculateExpectedDurationFromSpread(expectedDataForGoalOnDay.spread)
				assert dayActivityForGoal.totalActivityDurationMinutes == expectedDayDurationMinutes
				assert dayActivityForGoal.goalAccomplished == expectedDataForGoalOnDay.goalAccomplished
				assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForGoalOnDay.minutesBeyondGoal
				assert dayActivityForGoal.date == null // Only on week level
				assert dayActivityForGoal.timeZoneId == null // Only on week level
				assert dayActivityForGoal._links."yona:goal" == null // Only on week level
			}}
	}

	void assertDayDetail(User user, dayActivityOverviewResponse, goalUrl, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goalUrl)
		def dayActivityOverview = dayActivityOverviewResponse.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goalUrl}
		assert dayActivityForGoal?._links?."yona:dayDetails"?.href
		def dayActivityDetailUrl =  dayActivityForGoal?._links?."yona:dayDetails"?.href
		def response = appService.getResourceWithPassword(dayActivityDetailUrl, user.password)
		assert response.status == 200
		assert response.responseData.spread?.size() == 96
		assert response.responseData.totalActivityDurationMinutes ==  calculateExpectedDurationFromSpread(calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread))
		assert response.responseData.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert response.responseData.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert response.responseData.date =~ /\d{4}\-\d{2}\-\d{2}/
		assert response.responseData.timeZoneId == "Europe/Amsterdam"
		assert response.responseData._links."yona:goal"
	}

	int calculateExpectedDurationFromSpread(spread)
	{
		def dayDurationMinutes = 0
		spread.each { dayDurationMinutes += it.value }
		return dayDurationMinutes
	}

	def getExpectedDataForDayAndGoal(expectedValues, shortDay, goalUrl)
	{
		expectedValues[shortDay].find{it.goalUrl == goalUrl}.data
	}
}