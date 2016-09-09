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
import java.time.format.TextStyle
import java.time.temporal.ChronoField

import nu.yona.server.test.AnalysisService
import nu.yona.server.test.AppActivity
import nu.yona.server.test.AppService
import nu.yona.server.test.Buddy
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
	public String MULTIMEDIA_ACT_CAT_URL = appService.composeActivityCategoryUrl("eb7be352-b449-4d30-98fe-3be6ad555b43")

	@Shared
	public String COMMUNICATION_ACT_CAT_URL = appService.composeActivityCategoryUrl("90b9838f-1430-484b-94c1-e169318091cb")

	@Shared
	public String ADULT_CONTENT_ACT_CAT_URL = appService.composeActivityCategoryUrl("1f088f0b-9952-4ac7-bdc0-fd242238bc1d")

	@Shared
	public String SOCIAL_ACT_CAT_URL = appService.composeActivityCategoryUrl("27395d17-7022-4f71-9daf-f431ff4f11e8")

	@Shared
	private def fullDay = [ Sun: "SUNDAY", Mon : "MONDAY", Tue : "TUESDAY", Wed : "WEDNESDAY", Thu : "THURSDAY", Fri: "FRIDAY", Sat: "SATURDAY" ]

	User addRichard(boolean reload = true)
	{
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestamp")
		richard = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, richard)
		def response = appService.addGoal(richard, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assert response.status == 201
		return reload ? appService.reloadUser(richard) : richard
	}

	User addBob(boolean reload = true)
	{
		def bob = appService.addUser(appService.&assertUserCreationResponseDetails, "B o b", "Bob", "Dunn", "BD",
				"+$timestamp")
		bob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, bob)
		def response = appService.addGoal(bob, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assert response.status == 201
		return reload? appService.reloadUser(bob) : bob
	}

	User addBea(boolean reload = true)
	{
		def bea = appService.addUser(appService.&assertUserCreationResponseDetails, "B e a", "Bea", "Dundee", "BDD",
				"+$timestamp")
		bea = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, bea)
		return reload? appService.reloadUser(bea) : bea
	}

	def addRichardAndBobAsBuddies()
	{
		def richard = addRichard(false)
		def bob = addBob(false)
		appService.makeBuddies(richard, bob)
		return ["richard" : appService.reloadUser(richard), "bob" : appService.reloadUser(bob)]
	}

	def addRichardWithBobAndBeaAsBuddies()
	{
		def richard = addRichard(false)
		def bob = addBob(false)
		def bea = addBea(false)
		appService.makeBuddies(richard, bob)
		appService.makeBuddies(richard, bea)
		return ["richard" : appService.reloadUser(richard), "bob" : appService.reloadUser(bob), "bea" : appService.reloadUser(bea)]
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

	void assertEquals(String dateTimeString, ZonedDateTime comparisonDateTime, int epsilonSeconds = 10)
	{
		// Example date string: 2016-02-23T21:28:58.556+0000
		assertDateTimeFormat(dateTimeString)
		ZonedDateTime dateTime = YonaServer.parseIsoDateString(dateTimeString)
		assertEquals(dateTime, comparisonDateTime, epsilonSeconds)
	}

	void assertEquals(ZonedDateTime dateTime, ZonedDateTime comparisonDateTime, int epsilonSeconds = 10)
	{
		int epsilonMilliseconds = epsilonSeconds * 1000

		assert dateTime.isAfter(comparisonDateTime.minus(Duration.ofMillis(epsilonMilliseconds)))
		assert dateTime.isBefore(comparisonDateTime.plus(Duration.ofMillis(epsilonMilliseconds)))
	}

	void assertDateTimeFormat(dateTimeString)
	{
		assert dateTimeString ==~ /[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}\+\d{4}/
	}

	void assertMarkReadUnread(User user, message)
	{
		assert message.isRead == false
		def messageUrl = message._links.self.href
		assert message._links?."yona:markRead"?.href?.startsWith(messageUrl)

		def responseMarkRead = appService.postMessageActionWithPassword(message._links."yona:markRead".href, [ : ], user.password)
		assert responseMarkRead.status == 200
		assert responseMarkRead.responseData._embedded?."yona:affectedMessages"[0]?.isRead == true
		assert responseMarkRead.responseData._embedded?."yona:affectedMessages"[0]?._links?.self?.href == messageUrl
		assert responseMarkRead.responseData._embedded?."yona:affectedMessages"[0]?._links?."yona:markUnread"?.href.startsWith(messageUrl)

		def responseGetAfterMarkRead = appService.getResourceWithPassword(messageUrl, user.password)
		assert responseGetAfterMarkRead.status == 200
		assert responseGetAfterMarkRead.responseData.isRead == true
		assert responseGetAfterMarkRead.responseData._links?.self?.href == messageUrl
		assert responseGetAfterMarkRead.responseData._links?."yona:markUnread"?.href.startsWith(messageUrl)

		def responseMarkUnread = appService.postMessageActionWithPassword(responseGetAfterMarkRead.responseData._links?."yona:markUnread"?.href, [ : ], user.password)
		assert responseMarkUnread.status == 200
		assert responseMarkUnread.responseData._embedded?."yona:affectedMessages"[0]?.isRead == false
		assert responseMarkUnread.responseData._embedded?."yona:affectedMessages"[0]?._links?.self?.href == messageUrl
		assert responseMarkUnread.responseData._embedded?."yona:affectedMessages"[0]?._links?."yona:markRead"?.href.startsWith(messageUrl)

		def responseGetAfterMarkUnread = appService.getResourceWithPassword(messageUrl, user.password)
		assert responseGetAfterMarkUnread.status == 200
		assert responseGetAfterMarkUnread.responseData.isRead == false
		assert responseGetAfterMarkUnread.responseData._links?.self?.href == messageUrl
		assert responseGetAfterMarkUnread.responseData._links?."yona:markRead"?.href.startsWith(messageUrl)
	}

	def findActiveGoal(def response, def activityCategoryUrl)
	{
		response.responseData._embedded."yona:goals".find{ it._links."yona:activityCategory".href == activityCategoryUrl && !it.historyItem }
	}

	def findGoalsIncludingHistoryItems(def response, def activityCategoryUrl)
	{
		assert response.status == 200
		response.responseData._embedded."yona:goals".findAll{ it._links."yona:activityCategory".href == activityCategoryUrl }
	}

	void setGoalCreationTime(User user, activityCategoryURL, relativeCreationDateTimeString)
	{
		Goal goal = user.findActiveGoal(activityCategoryURL)
		goal.creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		def response = appService.updateGoal(user, goal.url, goal)
		assert response.status == 200
	}

	TimeZoneGoal addTimeZoneGoal(User user, activityCategoryURL, zones, relativeCreationDateTimeString)
	{
		ZonedDateTime creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		addTimeZoneGoal(user, activityCategoryURL, zones, creationTime)
	}

	TimeZoneGoal addTimeZoneGoal(User user, activityCategoryURL, zones, ZonedDateTime creationTime = YonaServer.now)
	{
		appService.addGoal(appService.&assertResponseStatusCreated, user, TimeZoneGoal.createInstance(creationTime, activityCategoryURL, zones.toArray()))
	}

	TimeZoneGoal updateTimeZoneGoal(User user, TimeZoneGoal updatedGoal, zones, relativeCreationDateTimeString)
	{
		ZonedDateTime updateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		appService.updateGoal(appService.&assertResponseStatusSuccess, user, updatedGoal.editURL, TimeZoneGoal.createInstance(updatedGoal, updateTime, zones.toArray()))
	}

	TimeZoneGoal updateTimeZoneGoal(User user, TimeZoneGoal updatedGoal, zones)
	{
		appService.updateGoal(appService.&assertResponseStatusSuccess, user, updatedGoal.editURL, TimeZoneGoal.createInstance(updatedGoal, YonaServer.now, zones.toArray()))
	}

	BudgetGoal addBudgetGoal(User user, activityCategoryURL, int maxDurationMinutes, relativeCreationDateTimeString)
	{
		ZonedDateTime creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		addBudgetGoal(user, activityCategoryURL, maxDurationMinutes, creationTime)
	}

	BudgetGoal addBudgetGoal(User user, activityCategoryURL, int maxDurationMinutes, ZonedDateTime creationTime = YonaServer.now)
	{
		appService.addGoal(appService.&assertResponseStatusCreated, user, BudgetGoal.createInstance(creationTime, activityCategoryURL, maxDurationMinutes))
	}

	BudgetGoal updateBudgetGoal(User user, BudgetGoal updatedGoal, int maxDurationMinutes, relativeCreationDateTimeString)
	{
		ZonedDateTime updateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		appService.updateGoal(appService.&assertResponseStatusSuccess, user, updatedGoal.editURL, BudgetGoal.createInstance(updatedGoal, updateTime, maxDurationMinutes))
	}

	BudgetGoal updateBudgetGoal(User user, BudgetGoal updatedGoal, int maxDurationMinutes)
	{
		appService.updateGoal(appService.&assertResponseStatusSuccess, user, updatedGoal.editURL, BudgetGoal.createInstance(updatedGoal, YonaServer.now, maxDurationMinutes))
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
	void reportNetworkActivity(User user, def categories, def url)
	{
		analysisService.postToAnalysisEngine(user, categories, url)
	}
	void reportNetworkActivity(User user, def categories, def url, relativeDateTimeString)
	{
		def response = analysisService.postToAnalysisEngine(user, categories, url, YonaServer.relativeDateTimeStringToZonedDateTime(relativeDateTimeString))
		assert response.status == 200
	}

	def getCurrentShortDay(ZonedDateTime dateTime = YonaServer.now)
	{
		dateTime.dayOfWeek.getDisplayName(TextStyle.SHORT, YonaServer.EN_US_LOCALE)
	}

	int getCurrentSpreadCell(ZonedDateTime dateTime)
	{
		dateTime.get(ChronoField.MINUTE_OF_DAY)/15
	}

	void assertWeekOverviewBasics(response, numberOfReportedGoals, expectedTotalElements, expectedPageSize = 2)
	{
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

	void assertNumberOfReportedDaysForGoalInWeekOverview(weekActivityOverview, Goal goal, numberOfReportedDays)
	{
		assert weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goal.url}
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goal.url}
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

	void assertDayInWeekOverviewForGoal(weekActivityOverview, Goal dayGoal, expectedValues, shortDay)
	{
		Goal weekGoal = findWeekGoal(expectedValues, dayGoal.activityCategoryUrl)
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, dayGoal)
		assert weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == weekGoal.url}
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == weekGoal.url}
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

	private Goal findWeekGoal(expectedValues, activityCategoryUrl)
	{
		def expectedValuesEndOfWeek = (expectedValues.size() == 1) ? expectedValues[getCurrentShortDay()] : expectedValues["Sat"]
		expectedValuesEndOfWeek.find{it.goal.activityCategoryUrl == activityCategoryUrl}.goal
	}

	private def assertDayOverviewForGoal(response, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goal)
		def dayActivityOverview = response.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		assert dayActivityOverview?.date =~ /\d{4}\-\d{2}\-\d{2}/
		assert dayActivityOverview.timeZoneId == "Europe/Amsterdam"
		assert dayActivityOverview.dayActivities?.size() == expectedValues[shortDay].size()
		// YD-203 assert dayActivityOverview._links?.self?.href
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goal.url}
		assert dayActivityForGoal.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread))
		assert dayActivityForGoal.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert dayActivityForGoal.date == null // Only on day overview level
		assert dayActivityForGoal.timeZoneId == null // Only on day overview level
		assert dayActivityForGoal._links."yona:dayDetails"
		assert dayActivityForGoal._links.self == null  // This is not a top level or embedded resource
		return dayActivityForGoal
	}

	void assertDayOverviewForTimeZoneGoal(response, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goal)
		def dayActivityForTimeZoneGoal = assertDayOverviewForGoal(response, goal, expectedValues, weeksBack, shortDay)
		assert dayActivityForTimeZoneGoal?.spread.size() == 96
	}

	void assertDayOverviewForBudgetGoal(response, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def dayActivityForBudgetGoal = assertDayOverviewForGoal(response, goal, expectedValues, weeksBack, shortDay)
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

	void assertWeekDetailForGoal(User user, weekActivityOverview, Goal goal, expectedValues)
	{
		def totalDurationMinutes = 0
		expectedValues.each { it.value.findAll{it.goal.url == goal.url}.each {it.data.spread.each { totalDurationMinutes += it.value }}}
		assert weekActivityOverview.weekActivities
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goal.url}
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl = weekActivityForGoal?._links?."yona:weekDetails"?.href
		def response = appService.getResourceWithPassword(weekActivityDetailUrl, user.password)
		assert response.status == 200
		assert response.responseData.spread?.size() == 96
		def expectedSpread = (0..95).collect { 0 }
		expectedValues.each { it.value.findAll{it.goal.url == goal.url}.each {it.data.spread.each { expectedSpread[it.key] += it.value }}}
		assert response.responseData.spread == expectedSpread
		assert response.responseData.totalActivityDurationMinutes == totalDurationMinutes
		assert response.responseData.date =~ /\d{4}\-W\d{2}/
		assert response.responseData.timeZoneId == "Europe/Amsterdam"
		assert response.responseData._links?."yona:goal"
		def activeDays = 0
		expectedValues.each { activeDays += it.value.findAll{it.goal.activityCategoryUrl == goal.activityCategoryUrl}.size()}
		assert response.responseData.dayActivities?.size() == activeDays
		expectedValues.each {
			def day = it.key
			it.value.findAll{it.goal.activityCategoryUrl == goal.activityCategoryUrl}.each
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

	void assertDayDetail(User user, dayActivityOverviewResponse, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goal)
		def dayActivityOverview = dayActivityOverviewResponse.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goal.url}
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

	void assertDayOverviewWithBuddiesBasics(response, expectedSize, expectedTotalElements, expectedPageSize = 3)
	{
		assert response.status == 200
		if(expectedSize == 0)
		{
			assert response.responseData._embedded?."yona:dayActivityOverviews" == null
		}
		else
		{
			assert response.responseData._embedded?."yona:dayActivityOverviews"?.size() == expectedSize
		}
		assert response.responseData.page
		assert response.responseData.page.size == expectedPageSize
		assert response.responseData.page.totalElements == expectedTotalElements
		assert response.responseData._links?.self?.href
	}

	void assertDayOverviewWithBuddies(response, User actingUser, activityCategoryUrl, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def dayActivityOverview = response.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		int expectedUsersWithGoalInThisCategory = expectedValues.findAll{it.expectedValues[shortDay].find{it.goal.activityCategoryUrl == activityCategoryUrl}}.size()
		assert dayActivityOverview.date =~ /\d{4}\-\d{2}\-\d{2}/
		assert dayActivityOverview.timeZoneId == "Europe/Amsterdam"
		if (expectedUsersWithGoalInThisCategory == 0) {
			assert dayActivityOverview.dayActivities.find{ it._links."yona:activityCategory"?.href == activityCategoryUrl} == null
		} else {
			assert dayActivityOverview.dayActivities.find{ it._links."yona:activityCategory"?.href == activityCategoryUrl}
			def dayActivitiesForCategory = dayActivityOverview.dayActivities.find{ it._links."yona:activityCategory".href == activityCategoryUrl}
			assert dayActivitiesForCategory._links.size() == 1
			assert dayActivitiesForCategory.dayActivitiesForUsers.size() == expectedUsersWithGoalInThisCategory

			expectedValues.each {
				User userToAssert = it.user
				def expectedValuesForUser = it.expectedValues
				def expectedValuesForDayAndActivityCategory = getExpectedDataForDayAndActivityCategory(expectedValuesForUser, shortDay, activityCategoryUrl)
				if (expectedValuesForDayAndActivityCategory) {
					if (expectedValuesForDayAndActivityCategory.goal instanceof TimeZoneGoal) {
						assertDayOverviewWithBuddiesForTimeZoneGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
					} else {
						assertDayOverviewWithBuddiesForBudgetGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
					}
				}

			}
		}
	}

	private def assertDayOverviewWithBuddiesForGoal(dayActivitiesForCategory, User actingUser, User userToAssert, expectedValuesForDayAndActivityCategory)
	{
		def dayActivityOverviewForUser
		def dayDetailsUrlPrefix
		Goal goal = expectedValuesForDayAndActivityCategory.goal
		if (userToAssert == actingUser) {
			dayActivityOverviewForUser = dayActivitiesForCategory.dayActivitiesForUsers.find{it._links."yona:user"?.href?.startsWith(userToAssert.url)}
			dayDetailsUrlPrefix = userToAssert.url
			assert dayActivityOverviewForUser._links."yona:buddy" == null
			assert userToAssert.goals.find{it.url == expectedValuesForDayAndActivityCategory.goal.url} // Test the test data
		}
		else
		{
			Buddy buddyToAssert = actingUser.buddies.find{it.user.url == userToAssert.url}
			dayActivityOverviewForUser = dayActivitiesForCategory.dayActivitiesForUsers.find{it._links."yona:buddy"?.href == buddyToAssert.url}
			dayDetailsUrlPrefix = buddyToAssert.url
			assert dayActivityOverviewForUser._links."yona:user" == null
			assert buddyToAssert.goals.find{it.url == expectedValuesForDayAndActivityCategory.goal.url} // Test the test data
		}
		assert dayActivityOverviewForUser.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(calculateExpectedDurationFromSpread(expectedValuesForDayAndActivityCategory.data.spread))
		assert dayActivityOverviewForUser.goalAccomplished == expectedValuesForDayAndActivityCategory.data.goalAccomplished
		assert dayActivityOverviewForUser.totalMinutesBeyondGoal == expectedValuesForDayAndActivityCategory.data.minutesBeyondGoal
		assert dayActivityOverviewForUser.date == null // Not for an individual user
		assert dayActivityOverviewForUser.timeZoneId == null // Not for an individual user
		assert dayActivityOverviewForUser._links."yona:dayDetails"?.href.startsWith(dayDetailsUrlPrefix)
		assert dayActivityOverviewForUser._links."yona:goal".href == goal.url
		assert dayActivityOverviewForUser._links.self == null  // This is not a top level or embedded resource
		return dayActivityOverviewForUser
	}

	void assertDayOverviewWithBuddiesForTimeZoneGoal(dayActivitiesForCategory, User actingUser, User userToAssert, expectedValuesForDayAndActivityCategory)
	{
		def dayActivityOverviewForUser = assertDayOverviewWithBuddiesForGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
		assert dayActivityOverviewForUser?.spread.size() == 96
	}

	void assertDayOverviewWithBuddiesForBudgetGoal(dayActivitiesForCategory, User actingUser, User userToAssert, expectedValuesForDayAndActivityCategory)
	{
		def dayActivityOverviewForUser = assertDayOverviewWithBuddiesForGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
		assert dayActivityOverviewForUser?.spread == null
	}

	int calculateExpectedDurationFromSpread(spread)
	{
		def dayDurationMinutes = 0
		spread.each { dayDurationMinutes += it.value }
		return dayDurationMinutes
	}

	def getExpectedDataForDayAndGoal(expectedValues, shortDay, Goal goal)
	{
		assert expectedValues[shortDay].find{it.goal.url == goal.url}
		expectedValues[shortDay].find{it.goal.url == goal.url}.data
	}

	def getExpectedDataForDayAndActivityCategory(expectedValues, shortDay, activityCategoryUrl)
	{
		expectedValues[shortDay].find{it.goal.activityCategoryUrl == activityCategoryUrl}
	}
}
