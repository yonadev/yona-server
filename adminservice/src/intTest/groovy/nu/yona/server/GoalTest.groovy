package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest

class GoalTest extends AbstractYonaIntegrationTest {

	def 'Get all goals loaded from file'(){
		given:

		when:
			def response = adminService.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == adminServiceBaseURL + adminService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
	}
}
