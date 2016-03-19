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
	def 'Get days activity'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded
		response.responseData._embedded."yona:dayActivities"
		response.responseData._embedded."yona:dayActivities".size() == 1
		//response.responseData._embedded."yona:dayActivities"[0].spread
		response.responseData._embedded."yona:dayActivities"[0].totalActivityDurationMinutes == 0
		response.responseData._embedded."yona:dayActivities"[0].goalAccomplished == false
		response.responseData._embedded."yona:dayActivities"[0].date
		response.responseData._embedded."yona:dayActivities"[0].timeZoneId == "Europe/Amsterdam"
	}

	def 'Get weeks activity'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		response.status == 200
		response.responseData._embedded
		response.responseData._embedded."yona:weekActivities"
		response.responseData._embedded."yona:weekActivities".size() == 1
		//response.responseData._embedded."yona:weekActivities"[0].spread
		response.responseData._embedded."yona:weekActivities"[0].totalActivityDurationMinutes == 0
		response.responseData._embedded."yona:weekActivities"[0].date
		response.responseData._embedded."yona:weekActivities"[0].timeZoneId == "Europe/Amsterdam"
	}
}
