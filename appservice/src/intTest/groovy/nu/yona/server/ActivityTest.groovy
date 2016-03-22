/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Get day activity overviews'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded
		response.responseData._embedded."yona:dayActivityOverviews"
		response.responseData._embedded."yona:dayActivityOverviews".size() == 1
		def dayActivityOverview = response.responseData._embedded."yona:dayActivityOverviews"[0]
		dayActivityOverview._embedded."yona:dayActivities"
		dayActivityOverview._embedded."yona:dayActivities".size() == 1
		def dayActivityForGoal = dayActivityOverview._embedded."yona:dayActivities"[0]
		dayActivityForGoal.spread
		dayActivityForGoal.spread.size() <= 96
		dayActivityForGoal.totalActivityDurationMinutes == 1
		dayActivityForGoal.goalAccomplished == false
		dayActivityForGoal.totalMinutesBeyondGoal == 1
		dayActivityForGoal.date
		dayActivityForGoal.timeZoneId == "Europe/Amsterdam"
	}

	def 'Get week activity overviews'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded
		response.responseData._embedded."yona:weekActivityOverviews"
		response.responseData._embedded."yona:weekActivityOverviews".size() == 1
		def weekActivityOverview = response.responseData._embedded."yona:weekActivityOverviews"[0]
		weekActivityOverview._embedded."yona:weekActivities"
		weekActivityOverview._embedded."yona:weekActivities".size() == 1
		def weekActivityForGoal = weekActivityOverview._embedded."yona:weekActivities"[0]
		weekActivityForGoal.spread
		weekActivityForGoal.spread.size() <= 96
		weekActivityForGoal.totalActivityDurationMinutes == 1
		weekActivityForGoal.date
		weekActivityForGoal.timeZoneId == "Europe/Amsterdam"
	}
}
