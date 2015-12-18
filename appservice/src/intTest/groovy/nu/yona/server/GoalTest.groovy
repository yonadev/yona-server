/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class GoalTest extends AbstractAppServiceIntegrationTest
{
	def 'Get all goals'()
	{
		given:

		when:
		def response = appService.getAllGoals()

		then:
		response.status == 200
		response.responseData._links.self.href == appService.url + appService.GOALS_PATH
		response.responseData._embedded.goals.size() > 0
	}
}
