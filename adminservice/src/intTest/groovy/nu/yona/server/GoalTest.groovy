/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

package nu.yona.server

import groovy.json.*
import nu.yona.server.test.Service
import spock.lang.Shared
import spock.lang.Specification

class GoalTest extends Specification
{
	@Shared
	def adminServiceBaseURL = Service.getProperty('yona.adminservice.url', "http://localhost:8080")
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def 'Get all goals loaded from file'()
	{
		given:

		when:
		def response = adminService.getAllGoals()

		then:
		response.status == 200
		response.responseData._links.self.href == adminServiceBaseURL + adminService.GOALS_PATH
		response.responseData._embedded.goals.size() > 0
	}
}
