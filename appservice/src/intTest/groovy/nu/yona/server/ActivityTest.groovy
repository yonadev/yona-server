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
	def bobBudgetGoalGamblingUrl, bobBudgetGoalGamblingUrlForRichard, bobBudgetGoalNewsUrl, bobBudgetGoalNewsUrlForRichard, bobTimeZoneGoalUrl, bobTimeZoneGoalUrlForRichard
	def bobDailyActivityReportsUrlForRichard, bobWeeklyActivityReportsUrlForRichard
	def dateTime

	void setupTestScenario()
	{
		def richardAndBob = addRichardAndBobAsBuddies()
		richard = richardAndBob.richard
		bob = richardAndBob.bob
		appService.addGoal(appService.&assertResponseStatusCreated, bob, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!")
		def goals = appService.getGoals(bob)
		bobBudgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		bobBudgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		bobTimeZoneGoalUrl = findGoal(goals, SOCIAL_ACT_CAT_URL)._links.self.href

		def richardWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		assert richardWithBuddy.buddies != null
		assert richardWithBuddy.buddies.size() == 1
		def bobGoalsForRichard = richardWithBuddy.buddies[0].goals
		bobBudgetGoalGamblingUrlForRichard = bobGoalsForRichard.find{it.activityCategoryUrl == GAMBLING_ACT_CAT_URL}.url
		bobBudgetGoalNewsUrlForRichard = bobGoalsForRichard.find{it.activityCategoryUrl == NEWS_ACT_CAT_URL}.url
		bobTimeZoneGoalUrlForRichard = bobGoalsForRichard.find{it.activityCategoryUrl == SOCIAL_ACT_CAT_URL}.url
		bobDailyActivityReportsUrlForRichard = richardWithBuddy.buddies[0].dailyActivityReportsUrl
		assert bobDailyActivityReportsUrlForRichard
		bobWeeklyActivityReportsUrlForRichard = richardWithBuddy.buddies[0].weeklyActivityReportsUrl
		assert bobWeeklyActivityReportsUrlForRichard

		addSomeTestActivity(bob)
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
		def response = appService.getDayActivityOverviews(bob)

		then:
		testDayActivityOverviews(response, bobBudgetGoalGamblingUrl, bobBudgetGoalNewsUrl, bobTimeZoneGoalUrl)
	}

	def 'Get day activity overviews from buddy'()
	{
		given:
		setupTestScenario()

		when:
		def response = appService.getResourceWithPassword(bobDailyActivityReportsUrlForRichard, richard.password)

		then:
		testDayActivityOverviews(response, bobBudgetGoalGamblingUrlForRichard, bobBudgetGoalNewsUrlForRichard, bobTimeZoneGoalUrlForRichard)
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
		testDayActivityDetail(bob, bob.dailyActivityReportsUrl, bobBudgetGoalGamblingUrl, bobBudgetGoalNewsUrl)
	}

	def 'Get day activity detail from buddy'()
	{
		given:
		setupTestScenario()

		when:
		true

		then:
		testDayActivityDetail(richard, bobDailyActivityReportsUrlForRichard, bobBudgetGoalGamblingUrlForRichard, bobBudgetGoalNewsUrlForRichard)
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
		def response = appService.getWeekActivityOverviews(bob)

		then:
		testWeekActivityOverviews(response, bobBudgetGoalGamblingUrl, bobBudgetGoalNewsUrl)
	}

	def 'Get week activity overviews from buddy'()
	{
		given:
		setupTestScenario()

		when:
		def response = appService.getResourceWithPassword(bobWeeklyActivityReportsUrlForRichard, richard.password)

		then:
		testWeekActivityOverviews(response, bobBudgetGoalGamblingUrlForRichard, bobBudgetGoalNewsUrlForRichard)
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
		testWeekActivityDetail(bob, bob.weeklyActivityReportsUrl, bobBudgetGoalGamblingUrl, bobBudgetGoalNewsUrl)
	}

	def 'Get week activity detail from buddy'()
	{
		given:
		setupTestScenario()

		when:
		true

		then:
		testWeekActivityDetail(richard, bobWeeklyActivityReportsUrlForRichard, bobBudgetGoalGamblingUrlForRichard, bobBudgetGoalNewsUrlForRichard)
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
