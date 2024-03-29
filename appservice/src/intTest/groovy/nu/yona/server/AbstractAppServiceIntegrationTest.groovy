/*******************************************************************************
 * Copyright (c) 2015, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatusCreated
import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoField

import nu.yona.server.test.AdminService
import nu.yona.server.test.AnalysisService
import nu.yona.server.test.AppActivity
import nu.yona.server.test.AppService
import nu.yona.server.test.BatchService
import nu.yona.server.test.Buddy
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.Goal
import nu.yona.server.test.TimeZoneGoal
import nu.yona.server.test.User
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractAppServiceIntegrationTest extends Specification
{
	@Shared
	AnalysisService analysisService = new AnalysisService()

	@Shared
	AppService appService = new AppService()

	@Shared
	AdminService adminService = new AdminService()

	@Shared
	BatchService batchService = new BatchService()

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
	private def fullDay = [Sun: "SUNDAY", Mon: "MONDAY", Tue: "TUESDAY", Wed: "WEDNESDAY", Thu: "THURSDAY", Fri: "FRIDAY", Sat: "SATURDAY"]

	def setupSpec()
	{
	}

	def cleanupSpec()
	{
		appService.shutdown()
	}

	void enableConcurrentRequests(int maxConcurrentRequests)
	{
		appService.enableConcurrentRequests(maxConcurrentRequests)
	}

	User addRichard(boolean reload = true, def operatingSystem = "IOS", def language = "en-US")
	{
		def deviceName = makeDeviceName("Richard", operatingSystem)
		User richard = appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "Richard", "Quinn", "RQ",
				makeMobileNumber(timestamp), deviceName, operatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, null, ["Accept-Language": language])
		richard = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, richard)
		def response = appService.addGoal(richard, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assertResponseStatusCreated(response)
		return reload ? appService.reloadUser(richard) : richard
	}

	User addBob(boolean reload = true, def operatingSystem = "IOS", def language = "en-US")
	{
		def deviceName = makeDeviceName("Bob", operatingSystem)
		User bob = appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "Bob", "Dunn", "BD",
				makeMobileNumber(timestamp), deviceName, operatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, null, [:], ["Accept-Language": language])
		bob = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, bob)
		def response = appService.addGoal(bob, BudgetGoal.createNoGoInstance(NEWS_ACT_CAT_URL))
		assertResponseStatusCreated(response)
		return reload ? appService.reloadUser(bob) : bob
	}

	User addBea(boolean reload = true, def operatingSystem = "IOS", def language = "en-US")
	{
		def deviceName = makeDeviceName("Bea", operatingSystem)
		User bea = appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "Bea", "Dundee", "BDD",
				makeMobileNumber(timestamp), deviceName, operatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, null, ["Accept-Language": language])
		bea = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, bea)
		return reload ? appService.reloadUser(bea) : bea
	}

	User makeUserForBuddyRequest(User user, emailAddress, firstName = null, lastName = null)
	{
		def userJson = user.convertToJson()
		if (firstName)
		{
			userJson.firstName = firstName
		}
		if (lastName)
		{
			userJson.lastName = lastName
		}
		User buddUser = new User(userJson)
		buddUser.emailAddress = emailAddress
		return buddUser
	}

	def makeDeviceName(def userName, def operatingSystem)
	{
		(operatingSystem == "IOS") ? "$userName's iPhone" : "$userName's S8"
	}

	def addRichardAndBobAsBuddies()
	{
		def richard = addRichard(false)
		def bob = addBob(false)
		bob.emailAddress = "bob@dunn.com"
		appService.makeBuddies(richard, bob)
		return ["richard": appService.reloadUser(richard), "bob": appService.reloadUser(bob)]
	}

	def addRichardWithBobAndBeaAsBuddies()
	{
		def richard = addRichard(false)
		def bob = addBob(false)
		def bea = addBea(false)
		bob.emailAddress = "bob@dunn.net"
		bea.emailAddress = "bea@gmail.com"
		appService.makeBuddies(richard, bob)
		appService.makeBuddies(richard, bea)
		return ["richard": appService.reloadUser(richard), "bob": appService.reloadUser(bob), "bea": appService.reloadUser(bea)]
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

	static String makeMobileNumber(String timestamp)
	{
		"+3161" + timestamp[-7..-1]
	}

	void assertMarkReadUnread(User user, message)
	{
		assert message.isRead == false
		def messageUrl = message._links.self.href
		assert message._links?."yona:markRead"?.href?.startsWith(messageUrl)

		def responseMarkRead = appService.postMessageActionWithPassword(message._links."yona:markRead".href as String, [:], user.password)
		assertResponseStatusOk(responseMarkRead)
		assert responseMarkRead.json._embedded?."yona:affectedMessages"[0]?.isRead == true
		assert responseMarkRead.json._embedded?."yona:affectedMessages"[0]?._links?.self?.href == messageUrl
		assert responseMarkRead.json._embedded?."yona:affectedMessages"[0]?._links?."yona:markUnread"?.href?.startsWith(messageUrl)

		def responseGetAfterMarkRead = appService.getResourceWithPassword(messageUrl, user.password)
		assertResponseStatusOk(responseGetAfterMarkRead)
		assert responseGetAfterMarkRead.json.isRead == true
		assert responseGetAfterMarkRead.json._links?.self?.href == messageUrl
		assert responseGetAfterMarkRead.json._links?."yona:markUnread"?.href?.startsWith(messageUrl)

		def responseMarkUnread = appService.postMessageActionWithPassword(responseGetAfterMarkRead.json._links?."yona:markUnread"?.href as String, [:], user.password)
		assertResponseStatusOk(responseMarkUnread)
		assert responseMarkUnread.json._embedded?."yona:affectedMessages"[0]?.isRead == false
		assert responseMarkUnread.json._embedded?."yona:affectedMessages"[0]?._links?.self?.href == messageUrl
		assert responseMarkUnread.json._embedded?."yona:affectedMessages"[0]?._links?."yona:markRead"?.href?.startsWith(messageUrl)

		def responseGetAfterMarkUnread = appService.getResourceWithPassword(messageUrl, user.password)
		assertResponseStatusOk(responseGetAfterMarkUnread)
		assert responseGetAfterMarkUnread.json.isRead == false
		assert responseGetAfterMarkUnread.json._links?.self?.href == messageUrl
		assert responseGetAfterMarkUnread.json._links?."yona:markRead"?.href?.startsWith(messageUrl)
	}

	def updateLastStatusChangeTime(User user, Buddy buddy, relativeLastStatusChangeTimeString)
	{
		def lastStatusChangeTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeLastStatusChangeTimeString)
		appService.updateLastStatusChangeTime(user, buddy, lastStatusChangeTime)
	}

	def findActiveGoal(def response, def activityCategoryUrl)
	{
		response.json._embedded."yona:goals".find { it._links."yona:activityCategory".href == activityCategoryUrl && !it.historyItem }
	}

	def findGoalsIncludingHistoryItems(def response, def activityCategoryUrl)
	{
		assertResponseStatusOk(response)
		response.json._embedded."yona:goals".findAll { it._links."yona:activityCategory".href == activityCategoryUrl }
	}

	void setGoalCreationTime(User user, activityCategoryUrl, relativeCreationDateTimeString)
	{
		assert user.findActiveGoal(activityCategoryUrl)
		Goal goal = user.findActiveGoal(activityCategoryUrl)
		goal.creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		def response = appService.updateGoal(user, goal.url, goal)
		assertResponseStatusOk(response)
	}

	TimeZoneGoal addTimeZoneGoal(User user, String activityCategoryUrl, List<String> zones, String relativeCreationDateTimeString)
	{
		ZonedDateTime creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		addTimeZoneGoal(user, activityCategoryUrl, zones, creationTime)
	}

	TimeZoneGoal addTimeZoneGoal(User user, String activityCategoryUrl, List<String> zones, ZonedDateTime creationTime = YonaServer.now)
	{
		appService.addGoal(CommonAssertions.&assertResponseStatusCreated, user, TimeZoneGoal.createInstance(creationTime, activityCategoryUrl, zones.toArray())) as TimeZoneGoal
	}

	TimeZoneGoal updateTimeZoneGoal(User user, TimeZoneGoal updatedGoal, zones, relativeCreationDateTimeString)
	{
		ZonedDateTime updateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		appService.updateGoal(CommonAssertions.&assertResponseStatusSuccess, user, updatedGoal.editUrl, TimeZoneGoal.createInstance(updatedGoal, updateTime, zones.toArray())) as TimeZoneGoal
	}

	TimeZoneGoal updateTimeZoneGoal(User user, TimeZoneGoal updatedGoal, zones)
	{
		appService.updateGoal(CommonAssertions.&assertResponseStatusSuccess, user, updatedGoal.editUrl, TimeZoneGoal.createInstance(updatedGoal, YonaServer.now, zones.toArray())) as TimeZoneGoal
	}

	BudgetGoal addNoGoGoal(User user, String activityCategoryUrl, String relativeCreationDateTimeString)
	{
		ZonedDateTime creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		addBudgetGoal(user, activityCategoryUrl, 0, creationTime)
	}

	BudgetGoal addBudgetGoal(User user, String activityCategoryUrl, int maxDurationMinutes, String relativeCreationDateTimeString)
	{
		ZonedDateTime creationTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		addBudgetGoal(user, activityCategoryUrl, maxDurationMinutes, creationTime)
	}

	BudgetGoal addBudgetGoal(User user, String activityCategoryUrl, int maxDurationMinutes, ZonedDateTime creationTime = YonaServer.now)
	{
		appService.addGoal(CommonAssertions.&assertResponseStatusCreated, user, BudgetGoal.createInstance(creationTime, activityCategoryUrl, maxDurationMinutes)) as BudgetGoal
	}

	BudgetGoal updateBudgetGoal(User user, BudgetGoal updatedGoal, int maxDurationMinutes, String relativeCreationDateTimeString)
	{
		ZonedDateTime updateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString)
		appService.updateGoal(CommonAssertions.&assertResponseStatusSuccess, user, updatedGoal.editUrl, BudgetGoal.createInstance(updatedGoal, updateTime, maxDurationMinutes)) as BudgetGoal
	}

	BudgetGoal updateBudgetGoal(User user, BudgetGoal updatedGoal, int maxDurationMinutes)
	{
		appService.updateGoal(CommonAssertions.&assertResponseStatusSuccess, user, updatedGoal.editUrl, BudgetGoal.createInstance(updatedGoal, YonaServer.now, maxDurationMinutes)) as BudgetGoal
	}

	void reportAppActivity(User user, Device device, def appName, def relativeStartDateTimeString, relativeEndDateTimeString)
	{
		reportAppActivities(user, device, createAppActivity(appName, relativeStartDateTimeString, relativeEndDateTimeString))
	}

	AppActivity createAppActivity(def appName, def relativeStartDateTimeString, relativeEndDateTimeString)
	{
		def startDateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeStartDateTimeString)
		def endDateTime = YonaServer.relativeDateTimeStringToZonedDateTime(relativeEndDateTimeString)
		AppActivity.singleActivity(appName, startDateTime, endDateTime)
	}

	void reportAppActivities(User user, Device device, def appActivities)
	{
		appActivities.collect {
			def response = appService.postAppActivityToAnalysisEngine(user, device, it)
			assertResponseStatusNoContent(response)
		}
	}

	void reportNetworkActivity(Device device, def categories, def url)
	{
		analysisService.postToAnalysisEngine(device, categories, url)
	}

	void reportNetworkActivity(Device device, def categories, def url, relativeDateTimeString)
	{
		def response = analysisService.postToAnalysisEngine(device, categories, url, YonaServer.relativeDateTimeStringToZonedDateTime(relativeDateTimeString))
		assertResponseStatusNoContent(response)
	}

	def getCurrentShortDay(ZonedDateTime dateTime = YonaServer.now)
	{
		dateTime.dayOfWeek.getDisplayName(TextStyle.SHORT, YonaServer.EN_US_LOCALE)
	}

	int getCurrentSpreadCell(ZonedDateTime dateTime)
	{
		dateTime.get(ChronoField.MINUTE_OF_DAY) / 15
	}

	void setCreationTime(User user, def relativeCreationDateTimeString)
	{
		setCreationTime(user, YonaServer.relativeDateTimeStringToZonedDateTime(relativeCreationDateTimeString))
	}

	void setCreationTime(User user, ZonedDateTime dateTime)
	{
		def userJson = user.convertToJson()
		userJson.creationTime = YonaServer.toIsoDateTimeString(dateTime)
		def response = appService.updateUser(user.editUrl, userJson, user.password)
		assertResponseStatusOk(response)
	}

	void assertWeekOverviewBasics(response, numberOfReportedGoals, expectedTotalElements, expectedPageSize = 2)
	{
		assertResponseStatusOk(response)
		assert response.json.page
		assert response.json.page.size == expectedPageSize
		assert response.json.page.totalElements == expectedTotalElements
		assert response.json._links?.self?.href != null

		if (numberOfReportedGoals != null)
		{
			assert response.json._embedded?."yona:weekActivityOverviews"?.size() == numberOfReportedGoals.size()
			numberOfReportedGoals.eachWithIndex { numberOfGoals, weekIndex ->
				assert response.json._embedded."yona:weekActivityOverviews"[weekIndex]?.date =~ /\d{4}-W\d{2}/
				assert response.json._embedded."yona:weekActivityOverviews"[weekIndex].timeZoneId == "Europe/Amsterdam"
				assert response.json._embedded."yona:weekActivityOverviews"[weekIndex].weekActivities?.size() == numberOfGoals
				assert response.json._embedded."yona:weekActivityOverviews"[weekIndex]._links?.self?.href
			}
		}
	}

	void assertNumberOfReportedDaysForGoalInWeekOverview(weekActivityOverview, Goal goal, numberOfReportedDays)
	{
		assert weekActivityOverview.weekActivities.find { it._links."yona:goal".href == goal.url }
		def weekActivityForGoal = weekActivityOverview.weekActivities.find { it._links."yona:goal".href == goal.url }
		assert weekActivityForGoal.spread == null // Only in detail
		assert weekActivityForGoal.totalActivityDurationMinutes == null // Only in detail
		assert weekActivityForGoal.totalMinutesBeyondGoal == null // Only for day
		assert weekActivityForGoal.date == null // Only on week overview level
		assert weekActivityForGoal.timeZoneId == null // Only on week overview level
		assert weekActivityForGoal._links."yona:goal"
		assert weekActivityForGoal._links."yona:weekDetails"
		assert weekActivityForGoal._links.self == null // This is not a top level or embedded resource
		assert weekActivityForGoal?.dayActivities?.size() == numberOfReportedDays
	}

	void assertDayInWeekOverviewForGoal(weekActivityOverview, Goal dayGoal, expectedValues, String shortDay)
	{
		Goal weekGoal = findWeekGoal(expectedValues, dayGoal.activityCategoryUrl)
		Map<String, Object> expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, dayGoal)
		assert weekActivityOverview.weekActivities.find { it._links."yona:goal".href == weekGoal.url }
		def weekActivityForGoal = weekActivityOverview.weekActivities.find { it._links."yona:goal".href == weekGoal.url }
		assert weekActivityForGoal.dayActivities[fullDay[shortDay]]
		def dayActivityForGoal = weekActivityForGoal.dayActivities[fullDay[shortDay]]
		assert dayActivityForGoal.spread == null // Only in detail
		assert dayActivityForGoal.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread as Map<Integer, Integer>)
		assert dayActivityForGoal.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert dayActivityForGoal.date == null // Only on week overview level
		assert dayActivityForGoal.timeZoneId == null // Only on week overview level
		assert dayActivityForGoal._links."yona:goal" == null //already present on week
		assert dayActivityForGoal._links."yona:dayDetails"
		assert dayActivityForGoal._links.self == null // This is not a top level or embedded resource
	}

	private Goal findWeekGoal(expectedValues, activityCategoryUrl)
	{
		def expectedValuesEndOfWeek = (expectedValues.size() == 1) ? expectedValues[getCurrentShortDay()] : expectedValues["Sat"]
		expectedValuesEndOfWeek.find { it.goal.activityCategoryUrl == activityCategoryUrl }.goal
	}

	def assertActivityValues(User user, int weeksBack, expectedValuesInWeek, expectedTotalWeeks)
	{
		assertWeekActivityValues(user, weeksBack, expectedValuesInWeek, expectedTotalWeeks)
		assertDayActivityValues(user, weeksBack, expectedValuesInWeek)
	}

	def assertWeekActivityValues(User user, int weeksBack, expectedValuesInWeek, expectedTotalWeeks)
	{
		def responseWeekOverviews = appService.getWeekActivityOverviews(user)
		assertWeekOverviewBasics(responseWeekOverviews, null, expectedTotalWeeks)
		def weekOverviewLastWeek = responseWeekOverviews.json._embedded."yona:weekActivityOverviews"[weeksBack]
		user.getGoals().each {
			def goal = it
			assertWeekDetailForGoal(user, weekOverviewLastWeek, goal, expectedValuesInWeek)
		}
		return true
	}

	def assertDayActivityValues(User user, int weeksBack, expectedValuesInWeek)
	{
		int pageSize = (weeksBack + 1) * 7
		def responseDayOverviewsAll = appService.getDayActivityOverviews(user, ["size": pageSize])
		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = expectedValuesInWeek.size() + currentDayOfWeek + 1
		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, pageSize)
		expectedValuesInWeek.each {
			String day = it.key
			it.value.each {
				Goal goal = it.goal
				assertDayDetail(user, responseDayOverviewsAll, goal, expectedValuesInWeek, weeksBack, day)
			}
		}
		return true
	}

	private def assertDayOverviewForGoal(response, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		Map<String, Object> expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goal)
		def dayActivityOverview = response.json._embedded."yona:dayActivityOverviews"[dayOffset]
		assert dayActivityOverview?.date =~ /\d{4}-\d{2}-\d{2}/
		assert dayActivityOverview.timeZoneId == "Europe/Amsterdam"
		assert dayActivityOverview.dayActivities?.size() == expectedValues[shortDay].size()
		assert dayActivityOverview._links?.self?.href
		def dayActivityForGoal = dayActivityOverview.dayActivities.find { it._links."yona:goal".href == goal.url }
		assert dayActivityForGoal.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread as Map<Integer, Integer>)
		assert dayActivityForGoal.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert dayActivityForGoal.date == null // Only on day overview level
		assert dayActivityForGoal.timeZoneId == null // Only on day overview level
		assert dayActivityForGoal._links."yona:dayDetails"
		assert dayActivityForGoal._links.self == null // This is not a top level or embedded resource
		return dayActivityForGoal
	}

	void assertDayOverviewForTimeZoneGoal(response, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def dayActivityForTimeZoneGoal = assertDayOverviewForGoal(response, goal, expectedValues, weeksBack, shortDay)
		assert dayActivityForTimeZoneGoal?.spread?.size() == 96
	}

	void assertDayOverviewForBudgetGoal(response, Goal goal, expectedValues, weeksBack, shortDay)
	{
		def dayActivityForBudgetGoal = assertDayOverviewForGoal(response, goal, expectedValues, weeksBack, shortDay)
		assert dayActivityForBudgetGoal?.spread == null
	}

	void assertDayOverviewBasics(response, expectedSize, expectedTotalElements, expectedPageSize = 3)
	{
		assertResponseStatusOk(response)
		assert response.json._embedded?."yona:dayActivityOverviews"?.size() == expectedSize
		assert response.json.page
		assert response.json.page.size == expectedPageSize
		assert response.json.page.totalElements == expectedTotalElements
		assert response.json._links?.self?.href
	}

	void assertWeekDetailForGoal(User user, weekActivityOverview, Goal goal, Map<String, Object> expectedValues)
	{
		def totalDurationMinutes = 0
		expectedValues.each { it.value.findAll { it.goal.url == goal.url }.each { it.data.spread.each { totalDurationMinutes += it.value } } }
		assert weekActivityOverview.weekActivities
		def weekActivityForGoal = weekActivityOverview.weekActivities.find { it._links."yona:goal".href == goal.url }
		if (!weekActivityForGoal)
		{
			//apparently this goal is not included
			return
		}
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl = weekActivityForGoal?._links?."yona:weekDetails"?.href
		def response = appService.getResourceWithPassword(weekActivityDetailUrl, user.password)
		assertResponseStatusOk(response)
		assert response.json.spread?.size() == 96
		List<Integer> expectedSpread = (0..95).collect { 0 }
		expectedValues.each { it.value.findAll { it.goal.url == goal.url }.each { it.data.spread.each { expectedSpread[it.key] += it.value } } }
		assert response.json.spread == expectedSpread
		assert response.json.totalActivityDurationMinutes == totalDurationMinutes
		assert response.json.date =~ /\d{4}-W\d{2}/
		assert response.json.timeZoneId == "Europe/Amsterdam"
		assert response.json._links?."yona:goal"
		boolean isForBuddy = weekActivityDetailUrl.startsWith(YonaServer.stripQueryString(user.url) + "/buddies/")
		assert (response.json._links?."yona:buddy" != null) == isForBuddy
		def activeDays = 0
		expectedValues.each { activeDays += it.value.findAll { it.goal.activityCategoryUrl == goal.activityCategoryUrl }.size() }
		assert response.json.dayActivities?.size() == activeDays
		expectedValues.each {
			def day = it.key
			it.value.findAll { it.goal.activityCategoryUrl == goal.activityCategoryUrl }.each {
				def expectedDataForGoalOnDay = it.data
				assert response.json.dayActivities[fullDay[day]]
				def dayActivityForGoal = response.json.dayActivities[fullDay[day]]
				assert dayActivityForGoal.spread == null // Only in detail
				def expectedDayDurationMinutes = calculateExpectedDurationFromSpread(expectedDataForGoalOnDay.spread)
				assert dayActivityForGoal.totalActivityDurationMinutes == expectedDayDurationMinutes
				assert dayActivityForGoal.goalAccomplished == expectedDataForGoalOnDay.goalAccomplished
				assert dayActivityForGoal.totalMinutesBeyondGoal == expectedDataForGoalOnDay.minutesBeyondGoal
				assert dayActivityForGoal.date == null // Only on week level
				assert dayActivityForGoal.timeZoneId == null // Only on week level
				assert dayActivityForGoal._links."yona:goal" == null // Only on week level
			}
		}
	}

	def getWeekDetail(User user, weekActivityOverview, Goal goal)
	{
		def weekActivityForGoal = weekActivityOverview.weekActivities.find { it._links."yona:goal".href == goal.url }
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl = weekActivityForGoal._links."yona:weekDetails".href
		def response = appService.getResourceWithPassword(weekActivityDetailUrl, user.password)
		assertResponseStatusOk(response)

		return response.json
	}

	def getDayDetail(User user, dayActivityOverviewResponse, Goal goal, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		getDayDetail(user, dayActivityOverviewResponse, goal, dayOffset)
	}

	def getDayDetail(User user, dayActivityOverviewResponse, Goal goal, dayOffset)
	{
		def dayActivityOverview = dayActivityOverviewResponse.json._embedded."yona:dayActivityOverviews"[dayOffset]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find { it._links."yona:goal".href == goal.url }
		assert dayActivityForGoal?._links?."yona:dayDetails"?.href
		def dayActivityDetailUrl = dayActivityForGoal._links."yona:dayDetails".href
		def response = appService.getResourceWithPassword(dayActivityDetailUrl, user.password)
		assertResponseStatusOk(response)

		return response.json
	}

	void assertDayDetail(User user, dayActivityOverviewResponse, Goal goal, expectedValues, int weeksBack, String shortDay)
	{
		Map<String, Object> expectedDataForDayAndGoal = getExpectedDataForDayAndGoal(expectedValues, shortDay, goal)
		def dayDetail = getDayDetail(user, dayActivityOverviewResponse, goal, weeksBack, shortDay)
		assert dayDetail.spread?.size() == 96
		assert dayDetail.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(expectedDataForDayAndGoal.spread as Map<Integer, Integer>)
		assert dayDetail.goalAccomplished == expectedDataForDayAndGoal.goalAccomplished
		assert dayDetail.totalMinutesBeyondGoal == expectedDataForDayAndGoal.minutesBeyondGoal
		assert dayDetail.date =~ /\d{4}-\d{2}-\d{2}/
		assert dayDetail.timeZoneId == "Europe/Amsterdam"
		assert dayDetail._links."yona:goal"
		boolean isForBuddy = dayDetail._links.self.href.startsWith(YonaServer.stripQueryString(user.url) + "/buddies/")
		assert (dayDetail._links?."yona:buddy" != null) == isForBuddy
	}

	void assertDayOverviewWithBuddiesBasics(response, expectedSize, expectedTotalElements, expectedPageSize = 3)
	{
		assertResponseStatusOk(response)
		if (expectedSize == 0)
		{
			assert response.json._embedded?."yona:dayActivityOverviews" == null
		}
		else
		{
			assert response.json._embedded?."yona:dayActivityOverviews"?.size() == expectedSize
		}
		assert response.json.page
		assert response.json.page.size == expectedPageSize
		assert response.json.page.totalElements == expectedTotalElements
		assert response.json._links?.self?.href
	}

	void assertDayOverviewWithBuddies(response, User actingUser, activityCategoryUrl, expectedValues, weeksBack, shortDay)
	{
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def dayActivityOverview = response.json._embedded."yona:dayActivityOverviews"[dayOffset]
		int expectedUsersWithGoalInThisCategory = expectedValues.findAll { it.expectedValues[shortDay].find { it.goal.activityCategoryUrl == activityCategoryUrl } }.size()
		assert dayActivityOverview.date =~ /\d{4}-\d{2}-\d{2}/
		assert dayActivityOverview.timeZoneId == "Europe/Amsterdam"
		if (expectedUsersWithGoalInThisCategory == 0)
		{
			assert dayActivityOverview.dayActivities.find { it._links."yona:activityCategory"?.href == activityCategoryUrl } == null
		}
		else
		{
			assert dayActivityOverview.dayActivities.find { it._links."yona:activityCategory"?.href == activityCategoryUrl }
			assert dayActivityOverview._links?.self?.href
			def dayActivitiesForCategory = dayActivityOverview.dayActivities.find { it._links."yona:activityCategory".href == activityCategoryUrl }
			assert dayActivitiesForCategory._links.size() == 1
			assert dayActivitiesForCategory.dayActivitiesForUsers.size() == expectedUsersWithGoalInThisCategory

			expectedValues.each {
				User userToAssert = it.user
				def expectedValuesForUser = it.expectedValues
				def expectedValuesForDayAndActivityCategory = getExpectedDataForDayAndActivityCategory(expectedValuesForUser, shortDay, activityCategoryUrl)
				if (expectedValuesForDayAndActivityCategory)
				{
					if (expectedValuesForDayAndActivityCategory.goal instanceof TimeZoneGoal)
					{
						assertDayOverviewWithBuddiesForTimeZoneGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
					}
					else
					{
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
		if (userToAssert == actingUser)
		{
			dayActivityOverviewForUser = dayActivitiesForCategory.dayActivitiesForUsers.find { it._links."yona:user"?.href?.startsWith(userToAssert.url) }
			dayDetailsUrlPrefix = YonaServer.stripQueryString(userToAssert.url)
			assert dayActivityOverviewForUser._links."yona:buddy" == null
			assert userToAssert.goals.find { it.url == expectedValuesForDayAndActivityCategory.goal.url } // Test the test data
		}
		else
		{
			Buddy buddyToAssert = actingUser.buddies.find { it.user.url == userToAssert.url }
			dayActivityOverviewForUser = dayActivitiesForCategory.dayActivitiesForUsers.find { it._links."yona:buddy"?.href == buddyToAssert.url }
			dayDetailsUrlPrefix = buddyToAssert.url
			assert dayActivityOverviewForUser._links."yona:user" == null
			assert buddyToAssert.user.goals.find { it.url == expectedValuesForDayAndActivityCategory.goal.url }
			// Test the test data
		}
		assert dayActivityOverviewForUser.totalActivityDurationMinutes == calculateExpectedDurationFromSpread(expectedValuesForDayAndActivityCategory.data.spread)
		assert dayActivityOverviewForUser.goalAccomplished == expectedValuesForDayAndActivityCategory.data.goalAccomplished
		assert dayActivityOverviewForUser.totalMinutesBeyondGoal == expectedValuesForDayAndActivityCategory.data.minutesBeyondGoal
		assert dayActivityOverviewForUser.date == null // Not for an individual user
		assert dayActivityOverviewForUser.timeZoneId == null // Not for an individual user
		assert dayActivityOverviewForUser._links."yona:dayDetails"?.href?.startsWith(dayDetailsUrlPrefix)
		assert dayActivityOverviewForUser._links."yona:goal".href == goal.url
		assert dayActivityOverviewForUser._links.self == null // This is not a top level or embedded resource
		return dayActivityOverviewForUser
	}

	void assertDayOverviewWithBuddiesForTimeZoneGoal(dayActivitiesForCategory, User actingUser, User userToAssert, expectedValuesForDayAndActivityCategory)
	{
		def dayActivityOverviewForUser = assertDayOverviewWithBuddiesForGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
		assert dayActivityOverviewForUser?.spread?.size() == 96
	}

	void assertDayOverviewWithBuddiesForBudgetGoal(dayActivitiesForCategory, User actingUser, User userToAssert, expectedValuesForDayAndActivityCategory)
	{
		def dayActivityOverviewForUser = assertDayOverviewWithBuddiesForGoal(dayActivitiesForCategory, actingUser, userToAssert, expectedValuesForDayAndActivityCategory)
		assert dayActivityOverviewForUser?.spread == null
	}

	int calculateExpectedDurationFromSpread(Map<Integer, Integer> spread)
	{
		def dayDurationMinutes = 0
		spread.each { dayDurationMinutes += it.value }
		return dayDurationMinutes
	}

	Map<String, Object> getExpectedDataForDayAndGoal(Map<String, Object> expectedValues, String shortDay, Goal goal)
	{
		assert expectedValues[shortDay].find { it.goal.url == goal.url }
		expectedValues[shortDay].find { it.goal.url == goal.url }.data
	}

	Map<String, Object> getExpectedDataForDayAndActivityCategory(Map<String, Object> expectedValues, String shortDay, String activityCategoryUrl)
	{
		expectedValues[shortDay].find { it.goal.activityCategoryUrl == activityCategoryUrl } as Map<String, Object>
	}
}
