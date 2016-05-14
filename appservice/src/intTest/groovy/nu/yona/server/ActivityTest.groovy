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
	void addSomeTestActivity(User user)
	{
		analysisService.postToAnalysisEngine(user, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(user, ["social"], "http://www.facebook.com")
	}

	def 'Get day activity overviews'()
	{
		given:
		def bob = addBob()
		def goals = appService.getGoals(bob)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		def timeZoneGoalUrl = appService.addGoal(appService.&assertResponseStatusCreated, bob, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!").url
		addSomeTestActivity(bob)

		when:
		def response = appService.getDayActivityOverviews(bob)

		then:
		testDayActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl, timeZoneGoalUrl)
	}

	def 'Get day activity overviews from buddy'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		appService.addGoal(appService.&assertResponseStatusCreated, bob, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!")
		def richardWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		assert richardWithBuddy.buddies != null
		assert richardWithBuddy.buddies.size() == 1
		def dailyActivityReportsUrl = richardWithBuddy.buddies[0].dailyActivityReportsUrl
		def goals = richardWithBuddy.buddies[0].goals
		def budgetGoalGamblingUrl = goals.find{it.activityCategoryUrl == GAMBLING_ACT_CAT_URL}.url
		assert budgetGoalGamblingUrl != null
		def budgetGoalNewsUrl = goals.find{it.activityCategoryUrl == NEWS_ACT_CAT_URL}.url
		assert budgetGoalNewsUrl != null
		def timeZoneGoalUrl = goals.find{it.activityCategoryUrl == SOCIAL_ACT_CAT_URL}.url
		assert timeZoneGoalUrl != null
		addSomeTestActivity(bob)

		when:
		def response = appService.getResourceWithPassword(dailyActivityReportsUrl, richard.password)

		then:
		testDayActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl, timeZoneGoalUrl)
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
		def richard = addRichard()
		def goals = appService.getGoals(richard)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		addSomeTestActivity(richard)

		when:
		def overviewsResponse = appService.getDayActivityOverviews(richard)
		overviewsResponse.responseData._embedded."yona:dayActivityOverviews"
		overviewsResponse.responseData._embedded."yona:dayActivityOverviews".size() == 1
		def dayActivityOverview = overviewsResponse.responseData._embedded."yona:dayActivityOverviews"[0]
		dayActivityOverview._embedded."yona:dayActivities"
		dayActivityOverview._embedded."yona:dayActivities".size() == 2
		def dayActivityForGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		dayActivityForGoal?._links?.self?.href
		def response = appService.getResourceWithPassword(dayActivityForGoal._links.self.href, richard.password)

		then:
		response.status == 200
		response.responseData.spread
		response.responseData.spread.size() == 96
		response.responseData.totalActivityDurationMinutes == 1
		response.responseData.goalAccomplished == false
		response.responseData.totalMinutesBeyondGoal == 1
		response.responseData.date =~ /\d{4}\-\d{2}\-\d{2}/
		response.responseData.timeZoneId == "Europe/Amsterdam"
		response.responseData._links."yona:goal"
	}

	def 'Get week activity overviews'()
	{
		given:
		def richard = addRichard()
		def dateTime = ZonedDateTime.now(ZoneId.of("Europe/Amsterdam"))
		def goals = appService.getGoals(richard)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		addSomeTestActivity(richard)

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		testWeekActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl)
	}
	
	def 'Get week activity overviews from buddy'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def richardWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		assert richardWithBuddy.buddies != null
		assert richardWithBuddy.buddies.size() == 1
		def weeklyActivityReportsUrl = richardWithBuddy.buddies[0].weeklyActivityReportsUrl
		def goals = richardWithBuddy.buddies[0].goals
		def budgetGoalGamblingUrl = goals.find{it.activityCategoryUrl == GAMBLING_ACT_CAT_URL}.url
		assert budgetGoalGamblingUrl != null
		def budgetGoalNewsUrl = goals.find{it.activityCategoryUrl == NEWS_ACT_CAT_URL}.url
		assert budgetGoalNewsUrl != null
		addSomeTestActivity(bob)

		when:
		def response = appService.getResourceWithPassword(weeklyActivityReportsUrl, richard.password)
		
		then:
		testWeekActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl)
	}
	
	void testWeekActivityOverviews(response, budgetGoalGamblingUrl, budgetGoalNewsUrl)
	{
		def dateTime = ZonedDateTime.now(ZoneId.of("Europe/Amsterdam"))
		assert response.status == 200
		assert response.responseData._embedded
		assert response.responseData._embedded."yona:weekActivityOverviews"
		assert response.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = response.responseData._embedded."yona:weekActivityOverviews"[0]
		assert weekActivityOverview.date =~ /\d{4}\-W\d{2}/
		assert weekActivityOverview._embedded."yona:weekActivities"
		assert weekActivityOverview._embedded."yona:weekActivities".size() == 2
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
		def richard = addRichard()
		def dateTime = ZonedDateTime.now(ZoneId.of("Europe/Amsterdam"))
		def goals = appService.getGoals(richard)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		addSomeTestActivity(richard)

		when:
		def overviewsResponse = appService.getWeekActivityOverviews(richard)
		overviewsResponse.responseData._embedded."yona:weekActivityOverviews"
		overviewsResponse.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = overviewsResponse.responseData._embedded."yona:weekActivityOverviews"[0]
		weekActivityOverview._embedded."yona:weekActivities"
		weekActivityOverview._embedded."yona:weekActivities".size() == 2
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		weekActivityForGoal._links.self
		def response = appService.getResourceWithPassword(weekActivityForGoal._links.self.href, richard.password)

		then:
		response.status == 200
		response.responseData.spread
		response.responseData.spread.size() == 96
		response.responseData.totalActivityDurationMinutes == 1
		response.responseData.date =~ /\d{4}\-W\d{2}/
		response.responseData.timeZoneId == "Europe/Amsterdam"
		response.responseData._links."yona:goal"
		response.responseData._embedded[dateTime.getDayOfWeek().toString()]
		response.responseData._embedded.size() == 1
		def dayActivityForGoal = response.responseData._embedded[dateTime.getDayOfWeek().toString()]
		!dayActivityForGoal.spread //only in detail
		dayActivityForGoal.totalActivityDurationMinutes == 1
		dayActivityForGoal.goalAccomplished == false
		dayActivityForGoal.totalMinutesBeyondGoal == 1
		!dayActivityForGoal.date
		dayActivityForGoal.timeZoneId == "Europe/Amsterdam"
		!dayActivityForGoal._links."yona:goal" //already present on week
	}
}
