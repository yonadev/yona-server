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
		def response = appService.getDaysActivity(richard)

		then:
		response.status == 200
		response.responseData
	}

	def 'Get weeks activity'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")

		when:
		def response = appService.getWeeksActivity(richard)

		then:
		response.status == 200
		response.responseData
	}
}
