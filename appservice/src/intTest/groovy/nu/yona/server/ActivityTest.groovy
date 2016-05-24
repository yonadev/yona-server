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
import spock.lang.Ignore

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	@Ignore
	def 'New style test'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-2 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 01:18", "W-1 Mon 02:28")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Mon 08:45", "W-1 Mon 09:05"), createAppActivity("Facebook", "W-1 Mon 09:05", "W-1 Mon 09:28")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:54")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 11:02")
		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)

		def budgetGoalNewsUrl = richard.findGoal(NEWS_ACT_CAT_URL).url

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded?."yona:weekActivityOverviews"?.size() == 2
		response.responseData._links?.next?.href != null
		def weekActivityOverview = response.responseData._embedded."yona:weekActivityOverviews"[1]
		weekActivityOverview.date =~ /\d{4}\-W\d{2}/
		weekActivityOverview._embedded."yona:weekActivities"
		weekActivityOverview._embedded."yona:weekActivities".size() == 1
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalNewsUrl}
		!weekActivityForGoal.spread //only in detail
		!weekActivityForGoal.totalActivityDurationMinutes //only in detail
		!weekActivityForGoal.totalMinutesBeyondGoal //only for day
		!weekActivityForGoal.date
		weekActivityForGoal.timeZoneId == "Europe/Amsterdam"
		weekActivityForGoal._links."yona:goal"
		weekActivityForGoal._embedded[dateTime.getDayOfWeek().toString()]
		weekActivityForGoal._embedded.size() == 1
		def dayActivityForGoal = weekActivityForGoal._embedded[1]
		!dayActivityForGoal.spread //only in detail
		dayActivityForGoal.totalActivityDurationMinutes == 1
		dayActivityForGoal.goalAccomplished == false
		dayActivityForGoal.totalMinutesBeyondGoal == 1
		!weekActivityForGoal.date
		dayActivityForGoal.timeZoneId == "Europe/Amsterdam"
		!dayActivityForGoal._links."yona:goal" //already present on week

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Get day activity overviews'()
	{
		given:
		def richard = addRichard()
		def goals = appService.getGoals(richard)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		def timeZoneGoalUrl = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!").url
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richard, ["social"], "http://www.facebook.com")

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded
		response.responseData._embedded."yona:dayActivityOverviews"
		response.responseData._embedded."yona:dayActivityOverviews".size() == 1
		def dayActivityOverview = response.responseData._embedded."yona:dayActivityOverviews"[0]
		dayActivityOverview.date =~ /\d{4}\-\d{2}\-\d{2}/
		dayActivityOverview._embedded."yona:dayActivities"
		dayActivityOverview._embedded."yona:dayActivities".size() == 3
		def dayActivityForBudgetGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		dayActivityForBudgetGoal
		!dayActivityForBudgetGoal.spread
		dayActivityForBudgetGoal.totalActivityDurationMinutes == 1
		dayActivityForBudgetGoal.goalAccomplished == false
		dayActivityForBudgetGoal.totalMinutesBeyondGoal == 1
		!dayActivityForBudgetGoal.date
		dayActivityForBudgetGoal.timeZoneId == "Europe/Amsterdam"
		dayActivityForBudgetGoal._links."yona:goal"
		dayActivityForBudgetGoal._links.self

		//time zone goal should have spread
		def dayActivityForTimeZoneGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == timeZoneGoalUrl}
		dayActivityForTimeZoneGoal
		dayActivityForTimeZoneGoal.spread
		dayActivityForTimeZoneGoal.spread.size() == 96

		def inactivityDayActivityForBudgetGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == budgetGoalNewsUrl}
		inactivityDayActivityForBudgetGoal.totalActivityDurationMinutes == 0
		inactivityDayActivityForBudgetGoal.totalMinutesBeyondGoal == 0
	}

	def 'Get day activity detail'()
	{
		given:
		def richard = addRichard()
		def goals = appService.getGoals(richard)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

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
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded
		response.responseData._embedded."yona:weekActivityOverviews"
		response.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = response.responseData._embedded."yona:weekActivityOverviews"[0]
		weekActivityOverview.date =~ /\d{4}\-W\d{2}/
		weekActivityOverview._embedded."yona:weekActivities"
		weekActivityOverview._embedded."yona:weekActivities".size() == 2
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalGamblingUrl}
		!weekActivityForGoal.spread //only in detail
		!weekActivityForGoal.totalActivityDurationMinutes //only in detail
		!weekActivityForGoal.totalMinutesBeyondGoal //only for day
		!weekActivityForGoal.date
		weekActivityForGoal.timeZoneId == "Europe/Amsterdam"
		weekActivityForGoal._links."yona:goal"
		weekActivityForGoal._embedded[dateTime.getDayOfWeek().toString()]
		weekActivityForGoal._embedded.size() == 1
		def dayActivityForGoal = weekActivityForGoal._embedded[dateTime.getDayOfWeek().toString()]
		!dayActivityForGoal.spread //only in detail
		dayActivityForGoal.totalActivityDurationMinutes == 1
		dayActivityForGoal.goalAccomplished == false
		dayActivityForGoal.totalMinutesBeyondGoal == 1
		!weekActivityForGoal.date
		dayActivityForGoal.timeZoneId == "Europe/Amsterdam"
		!dayActivityForGoal._links."yona:goal" //already present on week

		def inactivityWeekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities".find{ it._links."yona:goal".href == budgetGoalNewsUrl}
		!inactivityWeekActivityForGoal._embedded."yona:dayActivities"
	}

	def 'Get week activity detail'()
	{
		given:
		def richard = addRichard()
		def dateTime = ZonedDateTime.now(ZoneId.of("Europe/Amsterdam"))
		def goals = appService.getGoals(richard)
		def budgetGoalGamblingUrl = findGoal(goals, GAMBLING_ACT_CAT_URL)._links.self.href
		def budgetGoalNewsUrl = findGoal(goals, NEWS_ACT_CAT_URL)._links.self.href
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

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
