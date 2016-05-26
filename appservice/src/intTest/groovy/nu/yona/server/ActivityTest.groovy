/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.ZoneId
import java.time.ZonedDateTime

import nu.yona.server.test.TimeZoneGoal
import nu.yona.server.test.User

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def bob, richard
	def richardBudgetGoalGamblingUrl, richardBudgetGoalGamblingUrlForBob, richardBudgetGoalNewsUrl, richardBudgetGoalNewsUrlForBob, richardTimeZoneGoalUrl, richardTimeZoneGoalUrlForBob
	def richardDailyActivityReportsUrlForBob, richardWeeklyActivityReportsUrlForBob
	def dateTime

	def 'New style test'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Mon 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		def budgetGoalNewsUrl = richard.findGoal(NEWS_ACT_CAT_URL).url
		def timeZoneGoalSocialUrl = richard.findGoal(SOCIAL_ACT_CAT_URL).url

		// TODO: Shift spread 8 cells up, see YD-256
		def expectedValuesLastWeek = [
			"Mon" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [5 : 15, 6 : 5]]]],
			"Tue" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [27 : 15, 28 : 10]]]],
			"Wed" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [60 : 1]]]], // TODO: Should be "goalAccomplished: false, minutesBeyondGoal: 1", see YD-251
			"Thu" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [46 : 1]]]],
			"Fri" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard)
		def responseDayOverviews = appService.getDayActivityOverviews(richard)

		then:
		responseWeekOverviews.status == 200
		responseWeekOverviews.responseData._embedded?."yona:weekActivityOverviews"?.size() == 2
		responseWeekOverviews.responseData._links?.next?.href != null
		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedGoalsInWeekOverview(weekOverviewLastWeek, 2)
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsUrl, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialUrl, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Sat")

		assertNumberOfReportedDaysInDayOverview(responseDayOverviews, 6)
		assertDayOverviewForBudgetGoal(responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayOverviewForTimeZoneGoal(responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForTimeZoneGoal(responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviews, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviews, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Sat")

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	void setupTestScenario()
	{
		def richardAndBob = addRichardAndBobAsBuddies()
		richard = richardAndBob.richard
		bob = richardAndBob.bob
		appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!")
		def goals = appService.getGoals(richard)
		richardBudgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		richardBudgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		richardTimeZoneGoalUrl = findGoal(goals, SOCIAL_ACT_CAT_URL)._links.self.href

		def bobWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, bob.url, true, bob.password)
		assert bobWithBuddy.buddies != null
		assert bobWithBuddy.buddies.size() == 1
		def richardGoalsForBob = bobWithBuddy.buddies[0].goals
		richardBudgetGoalGamblingUrlForBob = richardGoalsForBob.find{it.activityCategoryUrl == GAMBLING_ACT_CAT_URL}.url
		richardBudgetGoalNewsUrlForBob = richardGoalsForBob.find{it.activityCategoryUrl == NEWS_ACT_CAT_URL}.url
		richardTimeZoneGoalUrlForBob = richardGoalsForBob.find{it.activityCategoryUrl == SOCIAL_ACT_CAT_URL}.url
		richardDailyActivityReportsUrlForBob = bobWithBuddy.buddies[0].dailyActivityReportsUrl
		assert richardDailyActivityReportsUrlForBob
		richardWeeklyActivityReportsUrlForBob = bobWithBuddy.buddies[0].weeklyActivityReportsUrl
		assert richardWeeklyActivityReportsUrlForBob

		addSomeTestActivity(richard)
		dateTime = ZonedDateTime.now(ZoneId.of("Europe/Amsterdam"))
	}

	void addSomeTestActivity(User user)
	{
		analysisService.postToAnalysisEngine(user, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(user, ["social"], "http://www.facebook.com")
	}

	def 'Get day activity overviews'()
	{
		given:
		setupTestScenario()

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		testDayActivityOverviews(response, richardBudgetGoalGamblingUrl, richardBudgetGoalNewsUrl, richardTimeZoneGoalUrl)
	}

	def 'Get day activity overviews from buddy'()
	{
		given:
		setupTestScenario()

		when:
		def response = appService.getBuddyDayActivityOverviews(bob)

		then:
		testDayActivityOverviews(response, richardBudgetGoalGamblingUrlForBob, richardBudgetGoalNewsUrlForBob, richardTimeZoneGoalUrlForBob)
	}

	void testDayActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl, timeZoneGoalUrl)
	{
		assert response.status == 200
		assert response.responseData._embedded
		assert response.responseData._embedded."yona:dayActivityOverviews"
		assert response.responseData._embedded."yona:dayActivityOverviews".size() == 1
		def dayActivityOverview = response.responseData._embedded."yona:dayActivityOverviews"[0]
		assert dayActivityOverview.date =~ /\d{4}\-\d{2}\-\d{2}/
		assert dayActivityOverview._embedded."yona:dayActivities"
		assert dayActivityOverview._embedded."yona:dayActivities".size() == 3
		def dayActivityForBudgetGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		assert dayActivityForBudgetGoal
		assert !dayActivityForBudgetGoal.spread
		assert dayActivityForBudgetGoal.totalActivityDurationMinutes == 1
		assert dayActivityForBudgetGoal.goalAccomplished == false
		assert dayActivityForBudgetGoal.totalMinutesBeyondGoal == 1
		assert !dayActivityForBudgetGoal.date
		assert dayActivityForBudgetGoal.timeZoneId == "Europe/Amsterdam"
		assert dayActivityForBudgetGoal._links."yona:goal"
		assert dayActivityForBudgetGoal._links.self

		//time zone goal should have spread
		def dayActivityForTimeZoneGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == timeZoneGoalUrl}
		assert dayActivityForTimeZoneGoal
		assert dayActivityForTimeZoneGoal.spread
		assert dayActivityForTimeZoneGoal.spread.size() == 96

		def inactivityDayActivityForBudgetGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == budgetGoalNewsUrl}
		assert inactivityDayActivityForBudgetGoal.totalActivityDurationMinutes == 0
		assert inactivityDayActivityForBudgetGoal.totalMinutesBeyondGoal == 0
	}

	def 'Get day activity detail'()
	{
		given:
		setupTestScenario()

		when:
		true

		then:
		testDayActivityDetail(richard, richard.dailyActivityReportsUrl, richardBudgetGoalGamblingUrl, richardBudgetGoalNewsUrl)
	}

	def 'Get day activity detail from buddy'()
	{
		given:
		setupTestScenario()

		when:
		true

		then:
		testDayActivityDetail(bob, richardDailyActivityReportsUrlForBob, richardBudgetGoalGamblingUrlForBob, richardBudgetGoalNewsUrlForBob)
	}

	void testDayActivityDetail(fromUser, dailyActivityReportsUrl, budgetGoalGamblingUrl, budgetGoalNewsUrl)
	{
		def overviewsResponse = appService.getResourceWithPassword(dailyActivityReportsUrl, fromUser.password)
		assert overviewsResponse.responseData._embedded."yona:dayActivityOverviews"
		assert overviewsResponse.responseData._embedded."yona:dayActivityOverviews".size() == 1
		def dayActivityOverview = overviewsResponse.responseData._embedded."yona:dayActivityOverviews"[0]
		assert dayActivityOverview._embedded."yona:dayActivities"
		assert dayActivityOverview._embedded."yona:dayActivities".size() == 3
		def dayActivityForGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		assert dayActivityForGoal?._links?.self?.href
		def response = appService.getResourceWithPassword(dayActivityForGoal._links.self.href, fromUser.password)
		assert response.status == 200
		assert response.responseData.spread
		assert response.responseData.spread.size() == 96
		assert response.responseData.totalActivityDurationMinutes == 1
		assert response.responseData.goalAccomplished == false
		assert response.responseData.totalMinutesBeyondGoal == 1
		assert response.responseData.date =~ /\d{4}\-\d{2}\-\d{2}/
		assert response.responseData.timeZoneId == "Europe/Amsterdam"
		assert response.responseData._links."yona:goal"
	}

	def 'Get week activity overviews'()
	{
		given:
		setupTestScenario()

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		testWeekActivityOverviews(response, richardBudgetGoalGamblingUrl, richardBudgetGoalNewsUrl)
	}

	def 'Get week activity overviews from buddy'()
	{
		given:
		setupTestScenario()

		when:
		def response = appService.getBuddyWeekActivityOverviews(bob)

		then:
		testWeekActivityOverviews(response, richardBudgetGoalGamblingUrlForBob, richardBudgetGoalNewsUrlForBob)
	}

	void testWeekActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl)
	{
		assert response.status == 200
		assert response.responseData._embedded
		assert response.responseData._embedded."yona:weekActivityOverviews"
		assert response.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = response.responseData._embedded."yona:weekActivityOverviews"[0]
		assert weekActivityOverview.date =~ /\d{4}\-W\d{2}/
		assert weekActivityOverview._embedded."yona:weekActivities"
		assert weekActivityOverview._embedded."yona:weekActivities".size() == 3
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		assert !weekActivityForGoal.spread //only in detail
		assert !weekActivityForGoal.totalActivityDurationMinutes //only in detail
		assert !weekActivityForGoal.totalMinutesBeyondGoal //only for day
		assert !weekActivityForGoal.date
		assert weekActivityForGoal.timeZoneId == "Europe/Amsterdam"
		assert weekActivityForGoal._links."yona:goal"
		assert weekActivityForGoal._embedded[dateTime.getDayOfWeek().toString()]
		assert weekActivityForGoal._embedded.size() == 1
		def dayActivityForGoal = weekActivityForGoal._embedded[dateTime.getDayOfWeek().toString()]
		assert !dayActivityForGoal.spread //only in detail
		assert dayActivityForGoal.totalActivityDurationMinutes == 1
		assert dayActivityForGoal.goalAccomplished == false
		assert dayActivityForGoal.totalMinutesBeyondGoal == 1
		assert !weekActivityForGoal.date
		assert dayActivityForGoal.timeZoneId == "Europe/Amsterdam"
		assert !dayActivityForGoal._links."yona:goal" //already present on week

		def inactivityWeekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalNewsUrl}
		assert !inactivityWeekActivityForGoal._embedded."yona:dayActivities"
	}

	def 'Get week activity detail'()
	{
		given:
		setupTestScenario()

		when:
		true

		then:
		testWeekActivityDetail(richard, richard.weeklyActivityReportsUrl, richardBudgetGoalGamblingUrl, richardBudgetGoalNewsUrl)
	}

	def 'Get week activity detail from buddy'()
	{
		given:
		setupTestScenario()

		when:
		true

		then:
		testWeekActivityDetail(bob, richardWeeklyActivityReportsUrlForBob, richardBudgetGoalGamblingUrlForBob, richardBudgetGoalNewsUrlForBob)
	}

	void testWeekActivityDetail(fromUser, weeklyActivityReportsUrl, budgetGoalGamblingUrl, budgetGoalNewsUrl)
	{
		def overviewsResponse = appService.getResourceWithPassword(weeklyActivityReportsUrl, fromUser.password)
		assert overviewsResponse.responseData._embedded."yona:weekActivityOverviews"
		assert overviewsResponse.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = overviewsResponse.responseData._embedded."yona:weekActivityOverviews"[0]
		assert weekActivityOverview._embedded."yona:weekActivities"
		assert weekActivityOverview._embedded."yona:weekActivities".size() == 3
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		assert weekActivityForGoal._links.self
		def response = appService.getResourceWithPassword(weekActivityForGoal._links.self.href, fromUser.password)
		assert response.status == 200
		assert response.responseData.spread
		assert response.responseData.spread.size() == 96
		assert response.responseData.totalActivityDurationMinutes == 1
		assert response.responseData.date =~ /\d{4}\-W\d{2}/
		assert response.responseData.timeZoneId == "Europe/Amsterdam"
		assert response.responseData._links."yona:goal"
		assert response.responseData._embedded[dateTime.getDayOfWeek().toString()]
		assert response.responseData._embedded.size() == 1
		def dayActivityForGoal = response.responseData._embedded[dateTime.getDayOfWeek().toString()]
		assert !dayActivityForGoal.spread //only in detail
		assert dayActivityForGoal.totalActivityDurationMinutes == 1
		assert dayActivityForGoal.goalAccomplished == false
		assert dayActivityForGoal.totalMinutesBeyondGoal == 1
		assert !dayActivityForGoal.date
		assert dayActivityForGoal.timeZoneId == "Europe/Amsterdam"
		assert !dayActivityForGoal._links."yona:goal" //already present on week
	}
}
