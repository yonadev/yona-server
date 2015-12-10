package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest

class GoalTest extends AbstractAppServiceIntegrationTest
{
	def 'Get all goals'()
	{
		given:

		when:
			def response = newAppService.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == appServiceBaseURL + appService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
			
	}
}
