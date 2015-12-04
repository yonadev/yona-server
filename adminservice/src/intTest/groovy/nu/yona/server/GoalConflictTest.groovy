package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest

/**
 * This class will test the goal conflict handling. In order to do this first a user needs to be created. Then a goal needs to be 
 * set for the user. Then we will simulate the Smoothwall server by 
 * @author pgussow
 *
 */
class GoalConflictTest extends AbstractYonaIntegrationTest {

	def 'Setup - Get the gambling goal'(){
		given:

		when:
			def response = adminService.getAllGoals()
			response.dump();

		then:
			response.status == 200
			response.responseData._links.self.href == adminServiceBaseURL + adminService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
	}
}
