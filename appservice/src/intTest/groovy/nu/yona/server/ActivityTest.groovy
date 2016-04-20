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

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Get day activity overviews'()
	{
		given:
		def richard = addRichard()
		def timeZoneGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance("social", ["11:00-12:00"].toArray()), "Going to restrict my social time!")
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
		dayActivityOverview._embedded."yona:dayActivities".size() == 2
		def dayActivityForBudgetGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href != timeZoneGoal.url}
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
		def dayActivityForTimeZoneGoal = dayActivityOverview._embedded."yona:dayActivities".find{ it._links."yona:goal".href == timeZoneGoal.url}
		dayActivityForTimeZoneGoal
		dayActivityForTimeZoneGoal.spread
		dayActivityForTimeZoneGoal.spread.size() == 96
	}

	def 'Get day activity detail'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def overviewsResponse = appService.getDayActivityOverviews(richard)
		overviewsResponse.responseData._embedded."yona:dayActivityOverviews"
		overviewsResponse.responseData._embedded."yona:dayActivityOverviews".size() == 1
		def dayActivityOverview = overviewsResponse.responseData._embedded."yona:dayActivityOverviews"[0]
		dayActivityOverview._embedded."yona:dayActivities"
		dayActivityOverview._embedded."yona:dayActivities".size() == 1
		def dayActivityForGoal = dayActivityOverview._embedded."yona:dayActivities"[0]
		dayActivityForGoal._links.self
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
		weekActivityOverview._embedded."yona:weekActivities".size() == 1
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities"[0]
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
	}

	def 'Get week activity detail'()
	{
		given:
		def richard = addRichard()
		def dateTime = ZonedDateTime.now(ZoneId.of("Europe/Amsterdam"))
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def overviewsResponse = appService.getWeekActivityOverviews(richard)
		overviewsResponse.responseData._embedded."yona:weekActivityOverviews"
		overviewsResponse.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = overviewsResponse.responseData._embedded."yona:weekActivityOverviews"[0]
		weekActivityOverview._embedded."yona:weekActivities"
		weekActivityOverview._embedded."yona:weekActivities".size() == 1
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities"[0]
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
